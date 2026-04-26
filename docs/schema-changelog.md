# Schema versions

| Version | Migration file | Change |
|---------|----------------|--------|
| 0       | —              | Initial schema: `task`, `record` tables |
| 1       | `1.sqm`        | Added `tat_group` table; `group_id` FK on `task` and `record` |
| 2       | `2.sqm`        | Added `space`, `space_member` tables; `space_id` FK on `tat_group` |
| 3       | `3.sqm`        | Added `is_private` flag on `space`; auto-created private spaces for existing users |
