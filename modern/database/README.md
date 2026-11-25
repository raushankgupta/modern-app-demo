# Modern database

The modern app keeps using **SQLite** (no migration). `modernbank.db` is a *separate copy* built from the
legacy schema and seed SQL. The legacy files (`database/*.sql`, `legacybank.db`) are read-only inputs and are
never modified.

## Prerequisites

- `htpasswd` (see below)
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

Checks table/row counts, repeat-run safety, git-ignore, BCrypt password hash, and that legacy files are unchanged.

## Demo login and password hashing (Story D2)

After the legacy seed, `init.sh` runs the modern-only `post-init.sql`, which replaces the plain-text `admin`
password with a BCrypt hash (cost 10, 60 characters; fits the existing `PASSWORD VARCHAR(100)` column, so the
schema is unchanged). The legacy `database/seed-data.sql` is not edited.

- Demo login stays: **`admin` / `admin123`** (valid against the stored hash).
- The hash is computed at init time with `htpasswd` and substituted into `post-init.sql`'s placeholder, so no
  hash is committed to git. The salt is random, so the hash differs per run (all valid for `admin123`).
- Extra prerequisite: `htpasswd` (included with macOS; on Debian/Ubuntu `sudo apt install apache2-utils`).
