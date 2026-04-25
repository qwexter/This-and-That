# TaT — Web UI

SvelteKit 2 + Svelte 5 static SPA, PWA-ready. Frontend for the [TaT](../README.md) task manager backend.

## Stack

| Library         | Version   | Purpose                         |
|-----------------|-----------|---------------------------------|
| SvelteKit       | ^2.57.0   | App framework (static adapter)  |
| Svelte 5        | ^5.55.2   | UI — runes mode                 |
| TypeScript      | ^6.0.2    | Type safety                     |
| vite-plugin-pwa | ^1.2.0    | Service worker + manifest       |

## Structure

```
src/
├── lib/
│   ├── api.ts       — fetch wrapper, all /tasks endpoints
│   └── types.ts     — Task, AddTask, UpdateTask interfaces
└── routes/
    ├── +layout.svelte        — dark shell, "TaT" header
    ├── +page.svelte          — task list (todo / done split, add form)
    └── tasks/[id]/
        └── +page.svelte      — task detail editor (PATCH / DELETE)
```

## State

Svelte 5 runes only — no stores, no external state lib.
- `$state` per-component, `$derived` for todo/done filters, `$effect` for load on mount.

## API

Base URL: `VITE_API_URL` env var (default: same origin).

| Method | Path         | Description       |
|--------|--------------|-------------------|
| GET    | /tasks       | List tasks        |
| GET    | /tasks/{id}  | Get task          |
| POST   | /tasks       | Create task       |
| PATCH  | /tasks/{id}  | Partial update    |
| DELETE | /tasks/{id}  | Soft-delete       |

## PWA

- Manifest: name "This and That" / short "TaT", theme `#1a1a2e`, standalone display.
- Icons: `static/icons/icon-192.png`, `static/icons/icon-512.png`.
- Service worker: `NetworkFirst` for `/tasks/*` (5 s timeout → cache fallback), `autoUpdate` on new version.

## Dev

Backend must run on `localhost:8080`. Vite dev server proxies `/tasks` there.

```sh
npm install
npm run dev        # :5173
```

## Build

```sh
npm run build      # static output → build/
npm run preview    # preview production build
```

Set `VITE_API_URL` when frontend and backend are on different origins:

```sh
# .env
VITE_API_URL=http://your-backend-host:8080
```
