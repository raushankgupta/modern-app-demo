#!/usr/bin/env bash
# Builds modern/database/modernbank.db from the legacy schema and seed data.
#
# The legacy SQL files are read-only inputs; they are never modified.
# Behaviour: the script RECREATES modernbank.db from scratch on every run
# (any existing modernbank.db is deleted first), so running it twice is safe.
# The legacy legacybank.db is never touched.
#
# Usage: ./init.sh            (can be run from any directory)
# Requires: the sqlite3 command-line tool.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LEGACY_DB_DIR="$(cd "$SCRIPT_DIR/../../database" && pwd)"
SCHEMA_SQL="$LEGACY_DB_DIR/sqlite-schema.sql"
SEED_SQL="$LEGACY_DB_DIR/seed-data.sql"
TARGET_DB="$SCRIPT_DIR/modernbank.db"

if ! command -v sqlite3 >/dev/null 2>&1; then
  echo "ERROR: sqlite3 command-line tool not found. Install it and retry (see README.md)." >&2
  exit 1
fi

for f in "$SCHEMA_SQL" "$SEED_SQL"; do
  if [ ! -f "$f" ]; then
    echo "ERROR: missing legacy SQL input: $f" >&2
    exit 1
  fi
done

rm -f "$TARGET_DB" "$TARGET_DB-journal" "$TARGET_DB-wal" "$TARGET_DB-shm"

sqlite3 "$TARGET_DB" < "$SCHEMA_SQL"
sqlite3 "$TARGET_DB" < "$SEED_SQL"

echo "Created $TARGET_DB"
sqlite3 "$TARGET_DB" <<'SQL'
SELECT 'EMPLOYEES: '          || COUNT(*) FROM EMPLOYEES;
SELECT 'CUSTOMERS: '          || COUNT(*) FROM CUSTOMERS;
SELECT 'ACCOUNTS: '           || COUNT(*) FROM ACCOUNTS;
SELECT 'TRANSACTIONS: '       || COUNT(*) FROM TRANSACTIONS;
SELECT 'REFERENCE_SEQUENCE max: ' || MAX(REF_ID) FROM REFERENCE_SEQUENCE;
SQL
