# Grouping

Groups collect tasks and records under a shared title. The home feed shows groups inline with their children, alongside ungrouped items, all sorted by creation date.

---

## Data model

```
tat_group
  id          TEXT PK
  owner_id    TEXT
  title       TEXT  (max 200 chars)
  created_at  INTEGER (epoch ms)
  updated_at  INTEGER
  deleted_at  INTEGER (soft delete)

task / record
  group_id    TEXT NULL → tat_group(id)   (added in schema v2)
```

An item with `group_id = NULL` is ungrouped and appears as a standalone entry in the feed. An item with a `group_id` is owned by that group and only appears as a child — it is excluded from the feed's top-level UNION.

Items are **never deleted** when their group is deleted. On group delete, all items with that `group_id` are set to `NULL` (ungrouped) first, then the group is soft-deleted.

---

## Feed

`GET /feed` returns a paginated mix of groups and solo items, ordered by `created_at DESC`.

### How the UNION works

```sql
SELECT 'group'  AS kind, id, created_at FROM tat_group
  WHERE owner_id = ? AND group_deleted_at IS NULL
UNION ALL
SELECT 'task'   AS kind, id, created_at FROM task
  WHERE owner_id = ? AND task_deleted_at IS NULL AND group_id IS NULL
UNION ALL
SELECT 'record' AS kind, id, created_at FROM record
  WHERE owner_id = ? AND record_deleted_at IS NULL AND group_id IS NULL
ORDER BY created_at DESC
LIMIT ? OFFSET ?
```

After the page is fetched, children are loaded for each group ID on that page (two extra queries: one for tasks, one for records). Children are sorted by `created_at DESC` within the group.

### Sort key

A group's position in the feed is its own `created_at`, **not** the newest child's timestamp. Adding items to a group does not bump it to the top.

### Pagination

Query params:

| Param | Default | Max | Description |
|-------|---------|-----|-------------|
| `limit` | 20 | 100 | Entries per page |
| `offset` | 0 | — | Skip N entries |

Response:

```json
{
  "items": [ ... ],
  "total": 42,
  "offset": 0,
  "limit": 20
}
```

`total` is the count of all feed entries (groups + ungrouped items) for the owner — use `total - offset - items.length` to determine if more pages exist.

### Entry shapes

Each `items` entry has a `kind` field as discriminator:

**Group entry:**
```json
{
  "kind": "group",
  "id": "...",
  "title": "Work stuff",
  "createdAt": "2026-04-25T10:00:00Z",
  "children": [
    {
      "kind": "task",
      "id": "...",
      "name": "Fix login bug",
      "description": null,
      "status": "Todo",
      "priority": "High",
      "deadline": null,
      "createdAt": "2026-04-25T09:00:00Z"
    },
    {
      "kind": "record",
      "id": "...",
      "title": "Meeting notes",
      "content": "...",
      "createdAt": "2026-04-24T14:00:00Z"
    }
  ]
}
```

**Solo task entry:**
```json
{
  "kind": "task",
  "id": "...",
  "groupId": null,
  "name": "Buy groceries",
  "description": null,
  "status": "Todo",
  "priority": "Low",
  "deadline": null,
  "createdAt": "2026-04-25T08:00:00Z"
}
```

**Solo record entry:**
```json
{
  "kind": "record",
  "id": "...",
  "groupId": null,
  "title": "Random thought",
  "content": "...",
  "createdAt": "2026-04-24T20:00:00Z"
}
```

---

## Group API

### Create

```
POST /groups
{ "title": "My group" }
→ 201 { "id": "...", "title": "My group" }
```

### Rename

```
PATCH /groups/{id}
{ "title": "New title" }
→ 200 { "id": "...", "title": "New title" }
```

### Delete

```
DELETE /groups/{id}
→ 204
```

Items in the group are **not** deleted. Their `group_id` is set to `NULL` — they become ungrouped and reappear in the feed as solo entries.

---

## Assigning items to groups

### Bulk add (recommended) — `POST /groups/{id}/items`

Accepts an array of items and writes all of them in a **single SQLite transaction**. All succeed or all fail — no partial state.

```
POST /groups/{id}/items
{
  "items": [
    { "kind": "newTask",      "name": "Fix bug", "priority": "High" },
    { "kind": "newRecord",    "title": "Meeting notes", "content": "..." },
    { "kind": "existingTask", "id": "<task-id>" },
    { "kind": "existingRecord", "id": "<record-id>" }
  ]
}
→ 200 { "items": [ ... ] }
```

Each result item has a `kind` field (`"task"` or `"record"`) and the full item shape.

**Errors:**
- `404` — group not found or belongs to another owner
- `400` — any item not found, blank name/title, or item already in a **different** group

If a task/record is already in **this** group the call is idempotent — it is included in the response without error.

### Single-item ops (low-level)

`groupId` is also accepted on the individual create/update endpoints:

```
POST   /tasks          { "name": "...", "groupId": "<group-id>" }
PATCH  /tasks/{id}     { "groupId": "<group-id>" }
PATCH  /tasks/{id}     { "clearGroup": true }
POST   /records        { "title": "...", "groupId": "<group-id>" }
PATCH  /records/{id}   { "groupId": "<group-id>" }
PATCH  /records/{id}   { "clearGroup": true }
```

`clearGroup: true` always wins over `groupId` if both are sent.

---

## Frontend

### Feed page (`/`)

Unified feed of all groups and ungrouped items. Groups render with their children expanded inline. Load-more button appears when `total > items.length`.

### Groups list (`/groups`)

Lists all groups. Create form at the top (title input). Delete button per row. Links to group detail.

### Group detail (`/groups/{id}`)

- Rename: click Edit, change title, Save.
- Delete: deletes the group (items become ungrouped, redirect to `/groups`).
- Tasks section: lists all tasks in this group. "↗" button removes task from group (`clearGroup: true`).
- Records section: same for records.
- **Add items bar** — three modes:
  - *New task* — inline form (name, description), calls `POST /groups/{id}/items` with `kind: "newTask"`
  - *New record* — inline form (title, content), calls `POST /groups/{id}/items` with `kind: "newRecord"`
  - *Add existing* — toggle between tasks/records, dropdown shows only ungrouped items, calls `POST /groups/{id}/items` with `kind: "existingTask"` or `"existingRecord"`

Each add is a single atomic API call. On success the page reloads to reflect the new state.

---

## Schema migration

Groups were added in schema version 2. The migration runs automatically on server startup for existing databases.

Migration file: `src/commonMain/sqldelight/migrations/1.sqm`

```sql
CREATE TABLE IF NOT EXISTS tat_group ( ... );
ALTER TABLE task ADD COLUMN group_id TEXT REFERENCES tat_group(id);
ALTER TABLE record ADD COLUMN group_id TEXT REFERENCES tat_group(id);
```

The server reads `PRAGMA user_version` on startup, compares to the compiled schema version, and runs any pending `.sqm` files in order. No manual intervention needed.

---

## Constraints

- An item belongs to **at most one group** — no many-to-many.
- `group_id` is a nullable FK — removing it from a group is always safe.
- Group title: max 200 characters, must not be blank.
- Group soft-delete does not cascade to items — items survive with `group_id = NULL`.
- Groups are per-owner — a user can only see and manage their own groups.
