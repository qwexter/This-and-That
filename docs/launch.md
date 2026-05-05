# Launch Guide

Three environments: **dev** (no auth, local binary), **auth-test** (full stack, local Docker), **prod** (Docker, Authelia, Caddy, real domain).

> **Status:** Dev mode is fully operational. Auth-test and prod require Docker config files not yet created — marked with `[TODO]`.

---

## Prerequisites

### Backend (all environments)
- JDK 21+ (only for build — not needed at runtime)
- Gradle wrapper included (`./gradlew`)

### Windows only
- SQLite bundled in `libs/windows/libsqlite3.a` — no extra install needed

### Linux only
```sh
sudo apt-get install -y libsqlite3-dev
sudo ln -sf /usr/lib/x86_64-linux-gnu/libsqlite3.so /usr/lib/libsqlite3.so
```

### Frontend
- Node.js 22+
- npm

### Auth-test / Prod
- Docker + Docker Compose

---

## Mode 1 — Dev (no auth, local binary)

No Docker. No auth. Hardcoded owner `dev-user`. Fastest iteration loop.

### 1. Build backend

```sh
# debug binary (fast build)
./gradlew linkDebugExecutableNative

# Windows output:  build/bin/native/debugExecutable/todo.exe
# Linux output:    build/bin/native/debugExecutable/todo.kexe
```

### 2. Run backend

```sh
# Windows
AUTH_MODE=none DB_PATH=tat.db build\bin\native\debugExecutable\todo.exe

# Linux / macOS
AUTH_MODE=none DB_PATH=tat.db ./build/bin/native/debugExecutable/todo.kexe
```

Backend listens on `http://localhost:8080`.

> **Schema migrations run automatically.** On startup the server compares `PRAGMA user_version` to the compiled schema version and applies any pending migrations. No manual DB reset needed after pulling schema changes.

### 3. Run frontend dev server

```sh
cd web
npm install
npm run dev       # Vite on :5173, proxies /tasks /records /groups /feed → localhost:8080
```

Open `http://localhost:5173`.

### Environment variables (backend)

| Variable | Default | Description |
|----------|---------|-------------|
| `AUTH_MODE` | `none` | Auth mode: `none` or `header` |
| `PORT` | `8080` | HTTP port |
| `HOST` | `0.0.0.0` | Bind address |
| `DB_PATH` | `tat.db` | SQLite file path |
| `STATIC_PATH` | _(unset)_ | If set, serves frontend files + disables CORS |

---

## Testing

### Run all tests

```sh
./gradlew allTests
```

### Run lint

```sh
./gradlew detekt
```

### Run frontend type check

```sh
cd web && npm run check
```

### What the tests cover

- `TasksRepositoryTest` — DB + in-memory repository logic
- `TasksRoutingTest` — REST endpoints, `AUTH_MODE=none`
- `TasksAuthTest` — ownership isolation, 401 responses, `AUTH_MODE=header`

---

## Mode 2 — Auth-test (full stack, local Docker)

Runs oauth2-proxy + backend + frontend in Docker on localhost. Uses Logto Cloud for auth (free tier, no self-hosting needed).

**Prerequisites:** Docker Desktop on Windows (WSL2 backend). The image builds a Linux binary inside Docker — no Linux machine needed.

### One-time Logto setup

1. Sign up at [cloud.logto.io](https://cloud.logto.io) — free tier (50 MAU)
2. Create application → **Traditional Web** → name: "TaT"
3. Set **Redirect URI**: `http://localhost/oauth2/callback`
4. Note `Endpoint`, `App ID`, `App Secret`
5. In **Sign-in experience** → enable username/password + email registration

### Configure env

```sh
cp .env.auth-test.example .env.auth-test
# Edit .env.auth-test — fill in LOGTO_ENDPOINT, LOGTO_APP_ID, LOGTO_APP_SECRET
# Generate OAUTH2_COOKIE_SECRET:
python -c "import secrets,base64; print(base64.b64encode(secrets.token_bytes(32)).decode())"
```

### Start

```sh
docker compose -f docker-compose.auth-test.yml --env-file .env.auth-test up --build
```

First build takes ~10 min (Kotlin/Native compile). Subsequent builds are cached.

- App: `http://localhost`
- Register an account via Logto UI on first visit

### Auth flow

```
Browser → oauth2-proxy:80
        → if not authed → redirect to Logto Cloud login/register
        → on success → injects X-Auth-Request-User (= Logto sub/userId)
        → proxies to Backend:8080
```

Backend reads `X-Auth-Request-User` as `userId`. `AUTH_MODE=header`.

### Services

| Service | Port | Role |
|---------|------|------|
| oauth2-proxy | 80 (public) | OIDC auth + reverse proxy |
| Backend (Ktor) | 8080 (internal) | API + static file serving |

### Public routes (no auth)

`GET /invites/{token}` is excluded from oauth2-proxy auth via `OAUTH2_PROXY_SKIP_AUTH_ROUTES`. Users can preview an invite before logging in.

---

## Mode 3 — Prod (Docker, real domain) `[TODO]`

Same as auth-test but with TLS, real SMTP, and persistent volumes.

> Docker Compose files not yet created. Files needed:
> - `docker-compose.prod.yml`
> - `config/authelia/configuration.yml` (uses env vars for secrets)
> - `config/Caddyfile.prod`
> - `.env.prod` (gitignored)

### Planned startup

```sh
# Set secrets
cp .env.prod.example .env.prod
# edit .env.prod — fill in real values

# Pull images + start
docker compose -f docker-compose.prod.yml up -d

# Check logs
docker compose -f docker-compose.prod.yml logs -f

# Add a user
docker compose -f docker-compose.prod.yml exec authelia \
  authelia crypto hash generate argon2 --password 'UserPassword'
# paste hash into config/authelia/users.yml
docker compose -f docker-compose.prod.yml restart authelia
```

### Planned services

| Service | Role |
|---------|------|
| Caddy | TLS termination, forward-auth, reverse proxy |
| Authelia | Auth UI, session management (SQLite) |
| Backend (Ktor) | API + static file serving |

### Planned environment secrets (`.env.prod`)

```sh
AUTHELIA_JWT_SECRET=<random 64 chars>
AUTHELIA_SESSION_SECRET=<random 64 chars>
AUTHELIA_STORAGE_ENCRYPTION_KEY=<random 32 chars>
SMTP_USER=noreply@yourdomain.com
SMTP_PASSWORD=<smtp password>
ZITADEL_MASTERKEY=              # unused if Authelia chosen
```

### Planned Caddyfile (prod)

```caddyfile
tat.yourdomain.com {
    request_header -X-User-Id
    request_header -X-User-Name

    forward_auth authelia:9091 {
        uri /api/authz/forward-auth
        copy_headers Remote-User Remote-Name Remote-Email
    }

    request_header X-User-Id {http.reverse_proxy.header.Remote-User}
    request_header X-User-Name {http.reverse_proxy.header.Remote-Name}

    reverse_proxy app:8080
}

auth.yourdomain.com {
    reverse_proxy authelia:9091
}
```

### Managing users in prod

No self-registration. Admin adds users manually:

1. Generate password hash:
   ```sh
   docker compose -f docker-compose.prod.yml exec authelia \
     authelia crypto hash generate argon2 --password 'NewUserPassword'
   ```
2. Add entry to `config/authelia/users.yml`:
   ```yaml
   users:
     username:
       displayname: "Display Name"
       password: "$argon2id$..."
       email: user@example.com
       groups: [users]
   ```
3. Restart Authelia:
   ```sh
   docker compose -f docker-compose.prod.yml restart authelia
   ```

---

## Build reference

| Task | Command |
|------|---------|
| Debug binary | `./gradlew linkDebugExecutableNative` |
| Release binary | `./gradlew linkReleaseExecutableNative` |
| Run debug | `./gradlew runDebugExecutableNative` |
| All tests | `./gradlew allTests` |
| Lint | `./gradlew detekt` |
| Frontend install | `./gradlew webInstall` or `cd web && npm install` |
| Frontend dev | `./gradlew webDev` or `cd web && npm run dev` |
| Frontend build | `./gradlew webBuild` or `cd web && npm run build` |

---

## Auth mode summary

| `AUTH_MODE` | Who resolves identity | When to use |
|-------------|----------------------|-------------|
| `none` | Hardcoded `dev-user` | Local dev, no auth needed |
| `header` | `X-User-Id` header from Caddy | Auth-test + prod behind Authelia |
