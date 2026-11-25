# Modern database

The modern app keeps using **SQLite** (no migration). `modernbank.db` is a *separate copy* built from the
legacy schema and seed SQL. The legacy files (`database/*.sql`, `legacybank.db`) are read-only inputs and are
never modified.

## Prerequisite

- `sqlite3` command-line tool (macOS ships with it; on Debian/Ubuntu: `sudo apt install sqlite3`;
  on Windows: install from <https://sqlite.org/download.html> or use WSL/Git Bash).

## Usage

```bash
cd modern/database
./init.sh
```

`init.sh` **recreates** `modernbank.db` from scratch every time (the old file is deleted first), so running it
twice is safe. Any data written by the app is lost on re-run. It runs, in order:

1. `database/sqlite-schema.sql` (legacy, read-only)
2. `database/seed-data.sql` (legacy, read-only)

Expected result: 5 tables, 1 employee, 10 customers, 15 accounts, 7 transactions, `REFERENCE_SEQUENCE` max id 6.

`modernbank.db` is git-ignored (see `modern/.gitignore`).

## Tests

```bash
./test-init.sh
```

Checks table/row counts, repeat-run safety, git-ignore and that legacy files are unchanged.
