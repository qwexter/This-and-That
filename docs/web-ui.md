# Web UI — Architecture & Current State

## Stack

| Layer | Choice |
|-------|--------|
| Framework | SvelteKit 2 + Svelte 5 (runes mode) |
| Build | Vite 8 + rolldown |
| Adapter | `adapter-static` → flat `build/` dir, SPA routing via `fallback: index.html` |
| PWA | `vite-plugin-pwa` 1.2 (Workbox `generateSW`) |
| Styling | CSS custom properties (design tokens), no CSS framework |
| State | Svelte 5 runes (`$state`, `$derived`, `$effect`); module-level `$state` in `.svelte.ts` for singletons |
| Auth | Authelia + Caddy; `X-User-Id` injected by reverse proxy — no login UI |

---

## Directory Layout

```
web/src/
  lib/
    api.ts               # API client — all fetch calls, SWR wrappers, offline mutate
    cache.ts             # Raw IndexedDB wrapper (no deps) — key/value per entity store
    sync.svelte.ts       # Mutation queue — IDB-persisted, LWW dedup, replay on reconnect
    types.ts             # Shared TypeScript types mirroring backend models
    ui/
      tokens.css         # Design token foundation (all CSS custom properties)
      BackLink.svelte
      Badge.svelte
      Button.svelte
      Card.svelte
      CheckCircle.svelte
      EmptyState.svelte
      FormField.svelte
      InlineError.svelte
      OfflineBanner.svelte
      SectionHeading.svelte
      Select.svelte
      Textarea.svelte
      TextInput.svelte
      Toaster.svelte
      toast.svelte.ts    # Toast singleton store
      index.ts           # Barrel export
  routes/
    +layout.svelte       # Root layout: theme, nav, Toaster, OfflineBanner, initSync
    +page.svelte         # Feed: spaces bar + paginated feed + FAB
    tasks/
      +page.svelte       # Task list with CheckCircle toggle, priority badge, done section
      [id]/+page.svelte  # Task detail form
    records/
      +page.svelte       # Record list
      [id]/+page.svelte  # Record detail form (5000-char textarea with counter)
    groups/
      +page.svelte       # Group list with space badge
      [id]/+page.svelte  # Group detail: inline title edit, space selector, add items panel
    spaces/
      +page.svelte       # Space list
      [id]/+page.svelte  # Space detail: members, groups
```

---

## Theming

CSS custom property tokens in `tokens.css`. Two themes:

- **Dark** (default): `:root, [data-theme="dark"]`
- **Light**: `@media (prefers-color-scheme: light) { :root:not([data-theme="dark"]) }` + `[data-theme="light"]` (separate blocks — lightningcss rejects nested media+selector)

Layout detects system theme via `window.matchMedia('(prefers-color-scheme: light)')` on mount. Live switching via `MediaQueryList.addEventListener('change')`. Manual toggle button in header overrides for the session (no localStorage persistence).

Token namespaces: `--color-bg-*`, `--color-text-*`, `--color-accent-*`, `--color-priority-*`, `--color-status-*`, `--color-kind-*`, `--color-space-*`, `--shadow-card`, `--space-*`, `--font-size-*`, `--radius-*`.

---

## UI Kit

All components live in `src/lib/ui/`. Zero hardcoded hex values in routes — everything through tokens.

| Component | Intent |
|-----------|--------|
| `Card` | Content container. `accent` prop maps to entity type or priority. Uses `border-top/right/bottom` (not shorthand) to avoid resetting `border-left` accent. |
| `Button` | `variant`: primary / secondary / danger / ghost / icon. `size`: sm / md. `active` prop for toggle state. |
| `Badge` | Semantic label. `variant`: priority-high/medium/low, status-done/todo, space-private/space-shared. `pill` for rounded. |
| `TextInput` | Single-line. Exposes `focus()` / `select()` methods via `bind:this`. Supports `autofocus`, `size` (sm/md). |
| `Textarea` | Multi-line. `showCounter` prop shows current/max char count. |
| `Select` | Styled native `<select>`. `onchange` callback receives selected value string. |
| `FormField` | Label + input wrapper. `id` prop links label to input. |
| `CheckCircle` | Task-domain toggle. Separate from Button to keep semantic intent clear. |
| `EmptyState` | Placeholder content. `variant`: page (centered, large) / inline (small) / error (red tint). |
| `BackLink` | ← navigation link. `href` + `label`. |
| `SectionHeading` | Section `<h2>` with accent underline. |
| `InlineError` | Inline error message below a form field. |
| `Toaster` | Fixed top-right toast container. Auto-dismiss 3.5s. |
| `OfflineBanner` | Fixed bottom pill. Shows offline state, queued count, replay progress, sync errors. |

Toast API (`toast.svelte.ts`): `toast.success(msg)`, `toast.error(msg)`, `toast.info(msg)`, `toast.dismiss(id)`. Module-level Svelte 5 `$state` singleton — reactive across all components without a store provider.

---

## Data Layer

### `api.ts` — three tiers

**GETs** use stale-while-revalidate:
```ts
api.getTasks(onCached, onFresh)
// onCached fires immediately from IDB if data exists → instant render
// network fetch runs in parallel → onFresh fires → silent in-place update
```

**Mutations (online)** — execute immediately, then update/invalidate IDB cache:
- `create*` → `cacheClear(resource)` (new ID from server, can't patch list)
- `update*` → `cachePut(resource, id, result)` + `cacheDelete(resource, 'list')`
- `delete*` → `cacheClear(resource)`

**Mutations (offline)** — `mutate()` detects `!navigator.onLine`:
- Creates throw `"Cannot create new items while offline"` (no server ID available to cache)
- Updates/deletes enqueue to IDB mutation queue, return `undefined`
- All callers guard: `if (updated) state = updated` — UI keeps current state when offline

### `cache.ts` — raw IndexedDB

DB: `tat-cache` v2. Stores: `tasks`, `records`, `groups`, `spaces`, `feed`, `mutation-queue`.

`mutation-queue` has index on `[resource, resourceId]` for LWW dedup lookups.

All operations non-fatal — cache failure never breaks the UI.

### `sync.svelte.ts` — mutation queue

**Enqueue (LWW dedup):**
- New `PATCH` → removes all older `PATCH` entries for same `(resource, resourceId)` → inserts new
- `DELETE` → removes ALL queued mutations for `(resource, resourceId)` → inserts delete (delete is terminal)
- `POST` (create) → not queueable, throws offline

**Replay (on `online` event + startup if online):**
- Replays queue ordered by `timestamp` ascending
- `200/204` → remove from queue, `cacheClear(resource)`
- `404` on DELETE → treat as success (already gone)
- `409` / `4xx` → discard mutation, set `lastError`
- Network error mid-replay → stop, retry on next `online` event
- Max 3 retries per entry before discard

**Reactive state** (`syncState`): `pendingCount`, `replaying`, `lastError` — all `$state`, read by `OfflineBanner`.

---

## Space Tabs (Feed)

Telegram-style horizontal tab strip above the feed. Only shown when `spaces.length > 1`.

**Tabs:** "All" + one per space. Private space labeled "My".

**Active tab** persisted to `localStorage` key `tat-active-tab` (value: `'all'` or space id). On load, if saved tab references a deleted space, resets to `'all'`.

**Feed filtering:** selecting a tab reloads feed with `?spaceId=<id>`. "All" omits the param.

**Space filter logic (backend):**
- Groups: `space_id = :space_id`
- Tasks/Records: in a group belonging to that space, OR ungrouped + space is private (ungrouped items implicitly belong to the private space)

**FAB context:** opening the group creation form pre-fills the space selector with the active tab's space (skipped if active tab is private/All — defaults to no space).

---

## Navigation

**Desktop (≥ 640px):** horizontal nav in header — Feed / Tasks / Records / Groups / Spaces + theme toggle.

**Mobile (< 640px):** header nav hidden, bottom tab bar shown. 5 tabs with icon + label. Active route highlighted via `$page.url.pathname`. Safe area inset (`env(safe-area-inset-bottom)`) for iPhone home bar. `main` gets matching bottom padding so content isn't obscured.

---

## Theming — FOUC Prevention

Inline `<script>` in `app.html` `<head>` runs synchronously before first paint:
```js
document.documentElement.dataset.theme =
  window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
```
Layout `$effect` keeps `document.documentElement.dataset.theme` in sync with reactive `theme` state after hydration. `data-theme` lives on `<html>` (= `:root`) — matches both `:root` token defaults and `[data-theme="light/dark"]` overrides in `tokens.css`.

---

## PWA

`vite-plugin-pwa` config in `vite.config.ts`:

- `registerType: autoUpdate` — SW updates silently in background
- Manifest: name "This and That", short_name "TaT", `display: standalone`, icons at 192/512
- Workbox `generateSW`: precaches all `js/css/html/ico/png/svg/woff2` (58 entries, ~255 KB)
- Runtime caching: `NetworkFirst` with 5s timeout for `/tasks|records|groups|spaces|feed` — falls back to SW cache when offline
- `theme-color` meta tag is dynamic — updates with light/dark toggle

---

## Keyboard UX

- All list-page add forms: Enter submits, focus returns to input after successful add
- All detail-page forms: Enter submits (where applicable)
- Inline edit fields (group title, space title): Enter saves, Escape cancels and restores original value
- Add panels (`groups/[id]`): first input gets `autofocus`, Enter submits, Escape cancels
- FAB sheet: Escape on form goes back to menu, Escape on menu closes sheet
- `TextInput` exposes `focus()` / `select()` via `bind:this` for programmatic focus management

---

## Known Issues / Backlog

| Item | Notes |
|------|-------|
| Search/filter on list pages | Client-side; data already loaded. `/tasks` by priority/status/name, `/records` by title/content, `/groups` by title/space |
| FAB sheet a11y | `role="dialog"` missing `tabindex="-1"` — Svelte a11y warning in build |
| Top nav active state | Bottom nav highlights active route; top nav does not |
| Sync errors silent in toast | `sync.svelte.ts` sets `lastError` but doesn't call `toast.error()` — conflicts only visible in `OfflineBanner` |
| Offline create UX | Throws error; could show clearer "go online to create" message |
| No `/me` endpoint integration | `offline-first-pwa.md` ADR notes needed to namespace IDB by userId for multi-device. Currently single-store, single-user assumed. |
| Space tabs — task/record space assignment | Ungrouped tasks/records created on a shared space tab are not filtered there (no direct `space_id` on task/record — only via group). Expected: create task on shared tab → prompt to assign a group, or silently lands in "All". |
