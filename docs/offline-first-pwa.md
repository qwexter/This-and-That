# Offline-First PWA Strategy

Decision record for mobile offline support in the TaT web app.

## Problem

Current service worker uses `NetworkFirst` with 5 s timeout for `/tasks`. This means:
- Read works offline only if previously cached
- All writes (POST/PATCH/DELETE) fail silently when offline
- No indication to user that data is unsynced

## Options Considered

### Option 1: Background Sync API

Browser queues failed requests in a sync queue, replays them automatically when connection returns. Service worker handles retry transparently.

**Flow:**
```
User action → SW intercepts → tries request
           → if offline → stores in IndexedDB queue + registers sync tag
           → on reconnect → browser fires sync event → SW replays queue
```

**Pros:**
- Browser owns retry logic
- Works across tabs and restarts
- Minimal app-level code

**Cons:**
- Not supported on iOS Safari (Chrome/Android only)
- Kills offline writes for a significant user segment

**Verdict:** Rejected. iOS gap is a blocker for a PWA targeting mobile.

---

### Option 2: Optimistic UI + Manual Sync Queue (recommended)

All state lives in IndexedDB locally. UI updates immediately on any action. A sync layer pushes changes to the server in the background and retries on reconnect.

**Flow:**
```
User action → write to IndexedDB → UI updates instantly
           → attempt server request in background
           → if fails → mark record syncStatus: 'pending'
           → on reconnect → flush pending queue → reconcile with server
```

**Conflict resolution:** server wins. Single-user isolation (each user's data is separate) means cross-device conflicts are rare and straightforward to resolve — server state is authoritative.

**Pros:**
- Works on iOS + Android + desktop
- Full offline — reads and writes
- No server-side changes needed

**Cons:**
- More app-level code than Option 1
- Pending state UI needed (sync indicator per task)

**Verdict:** Chosen approach.

---

### Option 3: CRDTs / Local-First Libraries

Libraries such as ElectricSQL, PowerSync, or Automerge provide conflict-free replicated data types with real-time sync.

**Pros:**
- Bulletproof multi-device conflict resolution
- Real-time collaboration possible

**Cons:**
- Significant server-side infrastructure changes
- Library complexity far exceeds requirements for a single-user todo app

**Verdict:** Rejected. Overkill for this use case.

---

## Chosen Architecture: Option 2

### Data ownership

```
IndexedDB  ←→  sync layer  ←→  server SQLite
(local truth)               (remote truth)
```

- On app load: fetch from server → hydrate IndexedDB
- On user action: write IndexedDB first → update UI → push to server
- On server failure: mark as `syncStatus: 'pending'`
- On reconnect: flush pending queue → clear flags on success

### Service worker change

Replace `NetworkFirst` with `CacheFirst` for `/tasks` reads.
Network fetch happens in background to keep cache fresh, not blocking UI.

### Dependencies

| Library | Purpose |
|---------|---------|
| `idb` (~1 kB) | Typed IndexedDB wrapper |

No additional dependencies beyond what is already in place.

### UI additions

- Sync status indicator per task (dot or spinner when `pending`)
- Global connectivity banner when offline
- `/me` endpoint on backend (required — see below)

---

## Auth Dependency: `/me` Endpoint

When offline the reverse proxy (`Caddy + Authelia`) is unreachable, so `X-User-Id` cannot be injected. The frontend must know the user's ID before going offline to namespace IndexedDB data correctly.

**Required backend route:**
```
GET /me → { "userId": "...", "displayName": "..." }
```

On first authenticated load, the app calls `/me`, stores `userId` in `sessionStorage`, and uses it as the IndexedDB namespace key. All local data is keyed by `userId`.

**Required Caddyfile addition:**
```caddyfile
request_header X-User-Name {http.reverse_proxy.header.Remote-Name}
```

---

## Work Breakdown

| Task | Effort |
|------|--------|
| `/me` endpoint on Ktor | Small |
| `X-User-Name` header in Caddyfile | Trivial |
| IndexedDB store (`idb` wrapper, keyed by `userId`) | Small |
| Sync queue (pending writes, retry on reconnect) | Medium |
| Reconnect detection + flush logic | Small |
| SW strategy: `NetworkFirst` → `CacheFirst` for reads | Small |
| Pending state UI indicators | Small |
| 401 redirect handling in `api.ts` | Trivial |
