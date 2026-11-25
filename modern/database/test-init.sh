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

# --- Story D2: BCrypt-hashed demo password (modern copy only) ---
PW="$(q "SELECT PASSWORD FROM EMPLOYEES WHERE USERNAME='admin'")"
check "admin password is not plain text"   "1" "$([ "$PW" != "admin123" ] && echo 1 || echo 0)"
check "admin password is a BCrypt hash"    "1" "$(echo "$PW" | grep -Eq '^\$2[ab]\$[0-9]{2}\$.{53}$' && echo 1 || echo 0)"
check "hash length fits VARCHAR(100) (60)" "60" "${#PW}"
check "legacy seed still has plain text (not edited)" "1" "$(grep -c "'admin', 'admin123'" "$ROOT/database/seed-data.sql")"
if command -v htpasswd >/dev/null 2>&1; then
  TMP="$(mktemp)"; echo "admin:${PW/\$2a\$/\$2y\$}" > "$TMP"
  htpasswd -vb "$TMP" admin admin123 >/dev/null 2>&1; check "admin123 verifies against hash" "0" "$?"
  htpasswd -vb "$TMP" admin wrongpw  >/dev/null 2>&1; check "wrong password is rejected"     "3" "$?"
  rm -f "$TMP"
fi

check "modernbank.db git-ignored" "0" "$(git -C "$ROOT" check-ignore -q "$DB"; echo $?)"
check "legacy src/database/pom.xml/legacybank.db unchanged" "$BEFORE" "$(legacy_hash)"

if [ "$FAILS" -ne 0 ]; then echo "$FAILS check(s) failed"; exit 1; fi
echo "All checks passed"
