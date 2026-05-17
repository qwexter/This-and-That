# VPS Configuration

This directory contains Podman Quadlet configuration for deploying TaT to a VPS.

## Structure

```
.config/
├── deploy.sh                    # Deployment script
├── README.md                    # This file
├── caddy/
│   ├── Caddyfile                # Your config (gitignored)
│   └── Caddyfile.example        # Template
├── containers/systemd/
│   ├── *.container              # Your configs (gitignored)
│   ├── *.volume                 # Your configs (gitignored)
│   ├── *.network                # Your configs (gitignored)
│   └── *.example                # Templates
└── tat/
    ├── oauth2-proxy.env         # Your secrets (gitignored)
    ├── oauth2-proxy.env.example # Template
    └── tat.db                   # Database (gitignored, lives on VPS)
```

## First-time Setup

1. **Copy templates to actual config files:**
   ```sh
   cd .config

   # Or just run deploy.sh - it will create missing files from .example
   ./deploy.sh user@your-vps
   ```

2. **Edit the config files with your values:**

   | File | What to change |
   |------|----------------|
   | `tat/oauth2-proxy.env` | Logto Cloud credentials + cookie secret |
   | `caddy/Caddyfile` | Your domain |
   | `containers/systemd/oauth2-proxy.container` | Your domain (2 places) |

3. **Generate cookie secret:**
   ```sh
   python3 -c "import secrets,base64; print(base64.b64encode(secrets.token_bytes(32)).decode())"
   ```

4. **Get Logto credentials:**
   - Go to [cloud.logto.io](https://cloud.logto.io)
   - Create app → Traditional Web → name: "TaT"
   - Set Redirect URI: `https://YOUR_DOMAIN/oauth2/callback`
   - Copy: Endpoint, App ID, App Secret

## Deployment

```sh
./deploy.sh user@your-vps-host
```

The script will:
1. Check all config files exist (create from .example if missing)
2. Validate no placeholder values remain
3. Sync files to VPS via rsync
4. Pull latest TaT image
5. Restart all services

## Manual VPS Commands

```sh
# Check service status
systemctl --user status systemd-tat oauth2-proxy systemd-caddy

# View logs
journalctl --user -u systemd-tat -f

# Restart a service
systemctl --user restart systemd-tat

# Pull new image manually
podman pull ghcr.io/qwexter/tat:latest
```

## Auth Flow

```
Browser → Caddy:443 (TLS) → oauth2-proxy:4180 (OIDC auth) → tat:8080 (backend)
                                    ↓
                            Logto Cloud (login)
```

oauth2-proxy adds `X-Auth-Request-User` header (Logto `sub` claim) to authenticated requests.
