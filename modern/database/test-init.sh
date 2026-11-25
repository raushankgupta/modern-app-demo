#!/usr/bin/env bash
# Tests for init.sh (Story D1): row counts, idempotency, git-ignore, legacy immutability.
# Usage: ./test-init.sh   (exit code 0 = all passed)

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DB="$SCRIPT_DIR/modernbank.db"
FAILS=0

check() { # name expected actual
  if [ "$2" = "$3" ]; then echo "PASS  $1"; else echo "FAIL  $1 (expected '$2', got '$3')"; FAILS=$((FAILS+1)); fi
}
q() { sqlite3 "$DB" "$1"; }

legacy_hash() {
  { find "$ROOT/src" "$ROOT/database" -type f | sort | xargs shasum -a 256
    shasum -a 256 "$ROOT/pom.xml"
    [ -f "$ROOT/legacybank.db" ] && shasum -a 256 "$ROOT/legacybank.db"; } | shasum -a 256
}

BEFORE="$(legacy_hash)"

"$SCRIPT_DIR/init.sh" >/dev/null
"$SCRIPT_DIR/init.sh" >/dev/null   # second run must also succeed (idempotent)
check "init.sh runs twice successfully" "0" "$?"

check "5 tables"               "5"  "$(q "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")"
check "1 employee"             "1"  "$(q "SELECT COUNT(*) FROM EMPLOYEES")"
check "10 customers"           "10" "$(q "SELECT COUNT(*) FROM CUSTOMERS")"
check "15 accounts"            "15" "$(q "SELECT COUNT(*) FROM ACCOUNTS")"
check "7 transactions"         "7"  "$(q "SELECT COUNT(*) FROM TRANSACTIONS")"
check "reference sequence max" "6"  "$(q "SELECT MAX(REF_ID) FROM REFERENCE_SEQUENCE")"
check "modernbank.db git-ignored" "0" "$(git -C "$ROOT" check-ignore -q "$DB"; echo $?)"
check "legacy src/database/pom.xml/legacybank.db unchanged" "$BEFORE" "$(legacy_hash)"

if [ "$FAILS" -ne 0 ]; then echo "$FAILS check(s) failed"; exit 1; fi
echo "All checks passed"
