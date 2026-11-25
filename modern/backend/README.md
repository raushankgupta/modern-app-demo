# Modern banking backend

Spring Boot 3.3 / Java 21 REST API that replaces the legacy Swing app. Data stays in SQLite.

## Prerequisites
- Java 21, Maven 3.9+
- The modern database: `cd ../database && ./init.sh` (creates `../database/modernbank.db`)

## Run
```bash
cd modern/backend
mvn spring-boot:run          # http://localhost:8080
curl localhost:8080/api/health   # {"status":"UP"}
```

## Test
```bash
mvn test
```
Tests use temporary database files only; `legacybank.db` and `modernbank.db` are never touched.

## Configuration
| Property | Env var | Default |
|----------|---------|---------|
| `bank.db.path` | `BANK_DB_PATH` | `../database/modernbank.db` (relative to `modern/backend`) |
| `bank.cors.allowed-origins` | `BANK_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` |

If the database file is missing, the app stops at startup with:
`SQLite database file not found: <path>. Create it with modern/database/init.sh or set bank.db.path (BANK_DB_PATH).`

## Error contract
All errors return JSON `{timestamp, status, error, message}` and never a stack trace:
`400` validation, `404` not found, `422` business rule violation, `500` unexpected.
