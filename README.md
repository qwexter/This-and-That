# This and That - TaT

TaT is a solution for small task and record management, like a todo-list with a few extras:
- [x] todo items with severity and optional deadline
- [x] plain text records
- [x] grouping of tasks and records
- [x] spaces — shared containers granting equal access to all groups inside
- [ ] PWA application to be able to work offline with cached data
# Tech stuff

## Tech Stack

| Library          | Version | Purpose                             |
|------------------|---------|-------------------------------------|
| Kotlin           | 2.3.0   | Language / Native compiler (LLVM)   |
| Ktor             | 3.4.2   | HTTP server (CIO engine, native)    |
| SQLDelight       | 2.3.2   | Type-safe SQL, native SQLite driver |
| kotlinx-datetime | 0.6.2   | Multiplatform date/time             |
| Detekt           | 1.23.8  | Static analysis + formatting        |

## Ktor Features

| Name                                                                               | Description                                                                        |
|------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| [Default Headers](https://start.ktor.io/p/default-headers)                        | Adds a default set of headers to HTTP responses                                    |
| [Routing](https://start.ktor.io/p/routing)                                        | Provides a structured routing DSL                                                  |
| [Resources](https://start.ktor.io/p/resources)                                    | Provides type-safe routing                                                         |
| [Status Pages](https://start.ktor.io/p/status-pages)                              | Provides exception handling for routes                                             |
| [Content Negotiation](https://start.ktor.io/p/content-negotiation)                | Provides automatic content conversion according to Content-Type and Accept headers |
| [kotlinx.serialization](https://start.ktor.io/p/kotlinx-serialization)            | Handles JSON serialization using kotlinx.serialization library                     |
| [SQLDelight](https://sqldelight.github.io/sqldelight/2.3.2/multiplatform_sqlite/) | Type-safe SQL with native SQLite driver, no JVM required                           |

## Building & Running

The project compiles to a native binary via Kotlin/Native (LLVM backend). No JVM required at runtime.
The target platform is selected automatically based on the host OS at build time.

| Task                                    | Description                      |
|-----------------------------------------|----------------------------------|
| `./gradlew linkDebugExecutableNative`   | Compile debug binary             |
| `./gradlew linkReleaseExecutableNative` | Compile optimized release binary |
| `./gradlew runDebugExecutableNative`    | Compile and run debug binary     |
| `./gradlew allTests`                    | Run tests for current platform   |
| `./gradlew detekt`                      | Run linter                       |

The compiled binary is output to:
```
build/bin/native/debugExecutable/todo.exe        # Windows
build/bin/native/debugExecutable/todo.kexe       # Linux / macOS
build/bin/native/releaseExecutable/todo.exe      # Windows release
build/bin/native/releaseExecutable/todo.kexe     # Linux / macOS release
```

### Windows prerequisite

On Windows, SQLite is bundled as a static library in `libs/windows/libsqlite3.a` (compiled with
Kotlin/Native's own clang — no MSYS2 or external toolchain required at build time).

## CI

GitHub Actions runs on every push and pull request to `main`:

| Job    | Runner          | What it does                       |
|--------|-----------------|------------------------------------|
| `lint` | `ubuntu-latest` | Runs `detekt` (style + coroutines) |
| `test` | `ubuntu-latest` | Runs `nativeTest` (linuxX64)       |

Tests run only after lint passes. The Kotlin/Native toolchain (`~/.konan`) is cached between runs.

Detekt config: `config/detekt/detekt.yml`. Active rule sets: `formatting`, `style`, `complexity`, `coroutines`, `naming`.

## API

The server listens on `http://0.0.0.0:8080`.

All timestamps are returned as ISO-8601 UTC strings. `deadline` is a local wall-clock datetime (`YYYY-MM-DDTHH:mm:ss`) with no timezone — the client owns timezone semantics for display.

### Tasks

| Method | Path         | Body | Description           |
|--------|--------------|------|-----------------------|
| GET    | /tasks       | —    | List all active tasks |
| GET    | /tasks/{id}  | —    | Get task by ID        |
| POST   | /tasks       | JSON | Create a new task     |
| PATCH  | /tasks/{id}  | JSON | Partial update a task |
| DELETE | /tasks/{id}  | —    | Soft-delete a task    |

`PATCH /tasks/{id}` accepts optional `groupId` (string) to assign task to a group, or `clearGroup: true` to remove it from its group.

### Records

| Method | Path           | Body | Description              |
|--------|----------------|------|--------------------------|
| GET    | /records       | —    | List all active records  |
| GET    | /records/{id}  | —    | Get record by ID         |
| POST   | /records       | JSON | Create a new record      |
| PATCH  | /records/{id}  | JSON | Partial update a record  |
| DELETE | /records/{id}  | —    | Soft-delete a record     |

`PATCH /records/{id}` accepts optional `groupId` / `clearGroup: true` (same as tasks).

### Groups

| Method | Path                   | Body | Description                                                        |
|--------|------------------------|------|--------------------------------------------------------------------|
| GET    | /groups                | —    | List all active groups                                             |
| GET    | /groups/{id}           | —    | Get group by ID                                                    |
| POST   | /groups                | JSON | Create group (`title` required, `spaceId` optional — defaults to private space) |
| PATCH  | /groups/{id}           | JSON | Update group: `title?`, `spaceId?` (move to space), `clearSpace: true` (move to private) |
| DELETE | /groups/{id}           | —    | Soft-delete group; items become ungrouped                          |
| POST   | /groups/{id}/items     | JSON | Add items to group atomically (new or existing tasks/records)      |

`POST /groups/{id}/items` accepts `{ "items": [...] }` where each entry has a `kind` discriminator:
- `"newTask"` — creates a task in the group (`name` required, `description`/`priority`/`deadline` optional)
- `"newRecord"` — creates a record in the group (`title` required, `content` optional)
- `"existingTask"` — assigns an ungrouped task by `id`
- `"existingRecord"` — assigns an ungrouped record by `id`

All items are written in a single transaction — all succeed or all fail. Returns 400 if any item is not found or already belongs to a different group.

### Spaces

| Method | Path                          | Body | Description                                          |
|--------|-------------------------------|------|------------------------------------------------------|
| GET    | /spaces                       | —    | List spaces accessible to caller (owned + member)    |
| GET    | /spaces/{id}                  | —    | Get space by ID (owner or member)                    |
| POST   | /spaces                       | JSON | Create space (`title`, max 200 chars)                |
| PATCH  | /spaces/{id}                  | JSON | Rename space (`title`) — owner only                  |
| DELETE | /spaces/{id}                  | —    | Soft-delete space — owner only                       |
| GET    | /spaces/{id}/members          | —    | List members — owner only                            |
| POST   | /spaces/{id}/members          | JSON | Add member (`userId`) — owner only                   |
| DELETE | /spaces/{id}/members/{userId} | —    | Remove member — owner only                           |

Every user has a **private space** (auto-created on first group creation). It cannot be deleted or have members added. Shared spaces are created explicitly via `POST /spaces`.

Groups are assigned to a space at creation time (`spaceId` in POST body) or later via `PATCH /groups/{id}`. If no `spaceId` is given at creation, the group goes into the caller's private space. `clearSpace: true` moves a group back to the private space.

`PATCH /groups/{id}` now accepts optional fields: `title`, `spaceId` (assign to space), `clearSpace: true` (remove from space).

### Feed

| Method | Path   | Body | Description                              |
|--------|--------|------|------------------------------------------|
| GET    | /feed  | —    | Unified paginated feed (groups + items)  |

Query params: `limit` (default 20, max 100), `offset` (default 0).

Feed response mixes groups (with children inline) and solo tasks/records, sorted by `created_at` desc. Each entry has a `kind` discriminator (`"group"`, `"task"`, `"record"`).

**Error responses:**

| Status | When                                        |
|--------|---------------------------------------------|
| 400    | Malformed JSON, missing required fields, blank name |
| 404    | Resource not found                          |
| 500    | Unexpected server error                     |

See `bruno/` for a [Bruno](https://www.usebruno.com/) collection with ready-to-run requests.

> to run locally: bru run --env local

## Web UI (PWA)

SvelteKit 2 + Svelte 5 static SPA with PWA support. Lives in `web/`.

| Library              | Version  | Purpose                        |
|----------------------|----------|--------------------------------|
| SvelteKit            | ^2.57.0  | App framework (static adapter) |
| Svelte               | ^5.55.2  | UI (runes mode)                |
| vite-plugin-pwa      | ^1.2.0   | Service worker + manifest      |
| TypeScript           | ^6.0.2   | Type safety                    |

### Dev

```sh
cd web
npm install
npm run dev        # Vite dev server on :5173, proxies /tasks → localhost:8080
```

Backend must be running on `localhost:8080` for API calls to work.

### Build

```sh
npm run build      # Static output → web/build/
npm run preview    # Preview production build
```

Serve `web/build/` with any static file server. Set `VITE_API_URL` env var when frontend and backend are on different origins:

```sh
# .env
VITE_API_URL=http://your-backend-host:8080
```

### PWA

Manifest name: **This and That** / short name **TaT**. Icons expected at `static/icons/icon-192.png` and `static/icons/icon-512.png`. Service worker uses `NetworkFirst` for `/tasks` with 5 s timeout fallback to cache.

## Database

SQLite database file `tat.db` is created in the working directory on first run.
Schema is managed by SQLDelight — `.sq` files live in `src/commonMain/sqldelight/`.
Migrations live in `src/commonMain/sqldelight/migrations/` as `<version>.sqm` files.

Schema version is stored in SQLite's `PRAGMA user_version`. On startup the server automatically runs any pending migrations — no manual steps needed.

Timestamps (`created_at`, `updated_at`, `deleted_at`) are stored as Unix epoch milliseconds (UTC). `deadline` is stored as epoch milliseconds (UTC) and round-tripped as a local datetime — no server-side timezone conversion occurs.
