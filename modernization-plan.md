# Legacy Banking App Modernization Plan

## 1. Overview

**Goal:** Modernize the Java Swing legacy banking app into a web app: React + TypeScript frontend, Java 21 + Spring Boot 3 backend, and the existing SQLite database (retained by decision).

**Guardrails**
- Legacy source (`src/`, `pom.xml`, `database/`, `run.*`) and `legacybank.db` are **read-only**. Nothing is modified, moved or deleted.
- All new code lives under `modern/` (`modern/database`, `modern/backend`, `modern/frontend`). This plan file is the only addition outside `modern/`.
- GitHub Issues, branches, commits and PRs go to `origin` = `https://github.com/raushankgupta/modern-app-demo` only.
- Do not commit `target/`, `node_modules/`, `legacybank.db` or `modernbank.db`.
- Database migration is not part of this plan (SQLite retained).

**Decisions confirmed with the user**

| Topic | Decision |
|-------|----------|
| Database | Retain SQLite. A separate copy `modern/database/modernbank.db` is built from the legacy schema and seed SQL. |
| UI library | Carbon Design System (`@carbon/react`) |
| Authentication | BCrypt-hashed passwords + stateless JWT (Spring Security). The seed password is hashed in the modern copy only. |
| Data access | Spring `JdbcTemplate` with explicit SQL (no JPA) |
| Epics | Three: `Database`, `Backend`, `Frontend` (no DevOps, no Docker) |
| Issue creation | In Agent mode via `gh` against `origin` |

---

## 2. Legacy analysis (evidence-based)

### 2.1 Architecture
- Java 8 Swing desktop app, plain JDBC, Maven, SQLite via `org.xerial:sqlite-jdbc`.
- SQL and business rules are embedded in the UI frames in `src/main/java/com/legacybank/`. Shared helpers are in `BankUtil.java`. Connection setup is in `DBConnection.java`, schema bootstrap in `DatabaseInitializer.java`, and the static session in `AppSession.java`.
- `DBConnection` opens SQLite with `foreign_keys=true`, `busy_timeout=10000` and `transaction_mode=IMMEDIATE`. Turning off auto-commit takes the write lock, so balance read and update are atomic.
- The DB file is `legacybank.db` (relative path, from `db.properties`). It is created on first run from `database/sqlite-schema.sql` and `database/seed-data.sql`.

### 2.2 Screens and functionality
| Legacy screen | Behaviour |
|---------------|-----------|
| `LoginFrame` | Username/password checked against `EMPLOYEES` (`STATUS='ACTIVE'`); plain-text compare; identity kept in static `AppSession`. |
| `MainFrame` | Menu bar + 6 buttons (no data shown). |
| `CustomerSearchFrame` | Search by Customer ID or Account Number; shows customer and all their accounts (ordered by account number). |
| `AccountDetailsFrame` | Account lookup: owner, type, status, balance, opened date; shortcuts to deposit, withdraw and history. |
| `DepositFrame` | Cash deposit, one atomic transaction. |
| `WithdrawalFrame` | Cash withdrawal, one atomic transaction. |
| `TransferFrame` | Debit + credit + two transaction rows in one atomic transaction. |
| `TransactionHistoryFrame` | History by account, optional type filter (`ALL`, `DEPOSIT`, `WITHDRAWAL`, `TRANSFER_DEBIT`, `TRANSFER_CREDIT`), newest first (`TRANSACTION_DATE DESC, TRANSACTION_ID DESC`). |

### 2.3 Business rules to preserve
- **Amount** (`BankUtil.parseAmount`): required, numeric, `> 0`, at most 2 decimal places, at most `10,000,000.00` per transaction.
  - Messages: "Please enter an amount.", "Amount must be a valid number.", "Amount must be greater than zero.", "Amount cannot have more than two decimal places.", "Amount cannot exceed 10,000,000.00 per transaction."
- **Account number** (`BankUtil.checkAccountNumber`): trimmed, 1–20 letters/digits.
- **Account status**: deposit, withdrawal and both transfer legs require `ACTIVE`. Seed has one `FROZEN` account (1001000008) and one `CLOSED` account (1001000013).
- **Funds**: withdrawal and transfer require `balance >= amount`. The DB also enforces `CHECK (BALANCE >= 0)`.
- **Transfer**: source and destination must differ. Both accounts must exist and be `ACTIVE`.
- **Reference numbers**: `DEP` / `WDR` / `TRF` + 10-digit zero-padded id from `REFERENCE_SEQUENCE` (`INSERT ... DEFAULT VALUES` then `last_insert_rowid()`), inside the same transaction. Both transfer legs share one reference.
- **Descriptions**: default "Cash deposit" / "Cash withdrawal", then " (by <username>)" appended. Transfer descriptions are "Transfer to <acct>" (debit leg) and "Transfer from <acct>" (credit leg), plus optional " - <note>" and " (by <username>)".
- **Money**: stored as `NUMERIC(15,2)`; read back as `BigDecimal` rounded to 2 places (`HALF_UP`).
- **Atomicity**: any failure rolls back all changes.

### 2.4 Database schema (`database/sqlite-schema.sql`)
| Table | Key columns / constraints |
|-------|---------------------------|
| `EMPLOYEES` | `EMPLOYEE_ID` PK AI; `USERNAME` UNIQUE; `PASSWORD` (plain text); `EMPLOYEE_NAME`; `STATUS` in (`ACTIVE`,`INACTIVE`) |
| `CUSTOMERS` | `CUSTOMER_ID` PK AI; `CUSTOMER_NAME`; `EMAIL`; `PHONE`; `ADDRESS`; `CREATED_DATE` (TEXT) |
| `ACCOUNTS` | `ACCOUNT_ID` PK AI; `ACCOUNT_NUMBER` UNIQUE; `CUSTOMER_ID` FK; `ACCOUNT_TYPE` in (`SAVINGS`,`CHECKING`,`FIXED_DEPOSIT`); `BALANCE` NUMERIC(15,2) `>= 0`; `STATUS` in (`ACTIVE`,`FROZEN`,`CLOSED`); `CREATED_DATE`; index on `CUSTOMER_ID` |
| `TRANSACTIONS` | `TRANSACTION_ID` PK AI; `ACCOUNT_NUMBER` FK (to `ACCOUNTS.ACCOUNT_NUMBER`); `TRANSACTION_TYPE` in the 4 types; `AMOUNT` `> 0`; `DESCRIPTION`; `TRANSACTION_DATE` TEXT; `REFERENCE_NUMBER`; indexes on (`ACCOUNT_NUMBER`,`TRANSACTION_DATE`) and `REFERENCE_NUMBER` |
| `REFERENCE_SEQUENCE` | `REF_ID` PK AI (sequence emulation) |

Seed data: 1 employee (`admin` / `admin123`), 10 customers, 15 accounts, 7 transactions, `REFERENCE_SEQUENCE` seeded to 6. Dates are `YYYY-MM-DD HH:MM:SS` local-time text.

### 2.5 Existing tests (reference for parity)
`BankUtilTest` (amount and account-number validation, formatting) and `DatabaseIntegrationTest` (seed volumes, idempotent init, atomic transfer commit and rollback, reference numbers, `CHECK` overdraft guard, history order). Both use temp DB files.

### 2.6 Gaps the modernization addresses
- Plain-text passwords and a static session (security).
- Dashboard shows no data, only buttons (UX).
- Business rules are buried in UI code (maintainability).

---

## 3. Target architecture

```
React 19 + TypeScript + Carbon (Vite, :5173)
        | REST/JSON + JWT Bearer
Spring Boot 3.3 / Java 21 (:8080)
  Controller -> Service -> Repository (JdbcTemplate)
        |
SQLite: modern/database/modernbank.db
        ^ built by modern/database/init.sh from legacy database/*.sql (read-only input)
```

**Repository layout (new)**
```
modern/
  database/   init.sh, post-init SQL (BCrypt hash), verify.sh, README.md
  backend/    Spring Boot Maven project (com.modernbank...)
  frontend/   Vite + React + TS + @carbon/react
```

**REST API (proposed)**
| Method + path | Purpose |
|---------------|---------|
| `POST /api/auth/login` | Returns JWT + employee name/username |
| `GET /api/customers/search?customerId=` or `?accountNumber=` | Customer and their accounts |
| `GET /api/accounts/{accountNumber}` | Account details with owner |
| `POST /api/transactions/deposit` | `{accountNumber, amount, description?}` |
| `POST /api/transactions/withdrawal` | `{accountNumber, amount, description?}` |
| `POST /api/transactions/transfer` | `{fromAccount, toAccount, amount, description?}` |
| `GET /api/accounts/{accountNumber}/transactions?type=` | History, newest first |
| `GET /api/dashboard/summary` | KPIs + 5 most recent transactions |

**Error contract:** JSON `{timestamp, status, error, message}`. `400` = validation failure, `401` = unauthenticated, `404` = not found, `422` = business rule violation (inactive account, insufficient funds, same account). Legacy message wording is reused where it exists.

**Dashboard UI enhancement (kept simple):** Carbon app shell (header with user and logout, side navigation). Dashboard: KPI tiles (customers, accounts, total balance, transactions), a Quick Actions tile grid replacing the 6 buttons, a "Recent transactions" table with type tags, and `ACTIVE`/`FROZEN`/`CLOSED` status tags on account views. Use Carbon components and tokens as-is, with minimal custom SCSS.

**Implementation sequence (high level)**
```
0. Create GitHub Epics + Stories
1. Database: D1 -> D2 -> D3
2. Backend foundation: B1 -> B2 -> B3
3. Backend features: B4 -> B5 -> B6 -> B7 -> B8 -> B9 -> B10
   (Frontend F1-F3 can start once B1/B3 exist)
4. Frontend: F1 -> F2 -> F3 -> F4 -> F5 -> F6 -> F7 -> F8 -> F9 -> F10
   (each screen follows its backend story)
5. Final validation (Stage 3 of the workshop)
```

---

## 4. GitHub structure

- **3 Epic issues**, titled exactly `Database`, `Backend`, `Frontend` (label `epic`).
- Each Story is titled `[DB] ...`, `[BE] ...` or `[FE] ...` (label `story` + component label) and linked **only** to its own component Epic (as a GitHub sub-issue of that Epic). No Story links to another Epic.
- Every Story body contains Description, Acceptance Criteria and a "Component" line.
- No Story spans two components (e.g. the dashboard is split into BE summary API `B9` and FE dashboard UI `F4`; each money operation is split into backend API and frontend screen).
- In Agent mode, create Issues with `gh` against `origin`. Confirm `git remote -v` first, then record the issue numbers back into this file.

---

## 5. Sub-tasks

Each sub-task is independent and reviewable. Every Story's acceptance criteria (AC) are the Issue body. Every Story also requires tests, run before completion.

### Sub-task 0 — Create GitHub Epics and Stories
- **Intent:** Establish the traceable backlog before any code is written.
- **Expected outcomes:** 3 Epics and 23 Stories exist in `origin`; each Story is a sub-issue of exactly one component Epic.
- **Todo:**
  1. Verify `origin` and `gh auth status`.
  2. Create labels (`epic`, `story`, `database`, `backend`, `frontend`).
  3. Create the Epics `Database`, `Backend`, `Frontend`.
  4. Create the 23 Stories below with their AC.
  5. Link each Story to its Epic as a sub-issue; verify no cross-component links.
  6. Record issue numbers in this file.
- **Relevant context:** Section 6 (Stories). Skill `github-cli`.
- **Status:** `[x] done`
- **Result (origin = https://github.com/raushankgupta/modern-app-demo):**

| Epic | Issue | Stories (issue numbers, all sub-issues of that Epic only) |
|------|-------|-----------------------------------------------------------|
| Database | #1 | D1 #4, D2 #5, D3 #6 |
| Backend | #2 | B1 #7, B2 #8, B3 #9, B4 #10, B5 #11, B6 #12, B7 #13, B8 #14, B9 #15, B10 #16 |
| Frontend | #3 | F1 #17, F2 #18, F3 #19, F4 #20, F5 #21, F6 #22, F7 #23, F8 #24, F9 #25, F10 #26 |

  Linkage verified through the sub-issues API: Epic #1 has 3 children, Epic #2 has 10 and Epic #3 has 10. Each Story has exactly one parent, its own component Epic.

---

## 6. Epics and Stories

### Epic: `Database`
Goal: A reproducible modern SQLite database built from the legacy schema and seed data, with the legacy files untouched.

#### D1 — [DB] Create modern database folder and init script
- **Intent:** Produce `modern/database/modernbank.db` from the legacy SQL without touching legacy assets.
- **Acceptance criteria**
  - `modern/database/init.sh` creates `modernbank.db` by running the legacy `database/sqlite-schema.sql` then `database/seed-data.sql` (read-only inputs).
  - Running the script twice is safe (idempotent or recreates cleanly; behaviour documented).
  - Result has 5 tables, 10 customers, 15 accounts, 7 transactions, 1 employee, `REFERENCE_SEQUENCE` max id 6.
  - `legacybank.db` and all files under `src/`, `database/`, `pom.xml` are not modified.
  - `modernbank.db` is git-ignored; `modern/database/README.md` documents usage.
- **Todo:** write `init.sh` (uses `sqlite3` CLI; fall back documented if missing); add `.gitignore` entry for `modernbank.db` under `modern/`; write README.
- **Context:** `database/sqlite-schema.sql`, `database/seed-data.sql`, `database/README.md`, `.gitignore`.
- **Status:** `[x] done` (Issue #4)

#### D2 — [DB] Hash seeded employee password in the modern database
- **Intent:** Remove the plain-text password from the modern database copy only.
- **Acceptance criteria**
  - After `init.sh`, `EMPLOYEES.PASSWORD` for `admin` is a BCrypt hash (`$2a$`/`$2b$`), not `admin123`.
  - The hash is applied by a modern-only SQL file executed by `init.sh`; the legacy `seed-data.sql` is not edited.
  - The demo login `admin` / `admin123` remains valid against the hash (documented in README).
  - Schema column `PASSWORD VARCHAR(100)` fits the hash (length 60) without schema changes.
- **Todo:** generate a BCrypt hash for the demo password; add `post-init.sql`; call it from `init.sh`.
- **Context:** `database/seed-data.sql` (line with `admin`), `database/sqlite-schema.sql` (EMPLOYEES).
- **Status:** `[x] done` (Issue #5)

#### D3 — [DB] Verify database integrity and legacy immutability
- **Intent:** Prove data integrity and that legacy assets stay unchanged.
- **Acceptance criteria**
  - `modern/database/verify.sh` checks table list, row counts, `PRAGMA foreign_key_check`, `PRAGMA integrity_check`, and the seeded `FROZEN`/`CLOSED` accounts.
  - It checks that `CHECK (BALANCE >= 0)` and `AMOUNT > 0` constraints reject bad inserts, using a scratch copy only.
  - It computes SHA-256 of legacy `src/`, `database/`, `pom.xml` (and `legacybank.db` if present) and compares with a stored baseline (`git diff` clean on legacy paths also accepted).
  - Script exits non-zero on any failure and prints a clear summary.
- **Todo:** write `verify.sh`; document in README.
- **Context:** `src/test/java/com/legacybank/DatabaseIntegrationTest.java` (parity of checks).
- **Status:** `[ ] pending`

### Epic: `Backend`
Goal: Spring Boot REST API that preserves all legacy business rules, atomicity and money precision.

#### B1 — [BE] Scaffold Spring Boot service
- **Intent:** Runnable, secured-ready service skeleton.
- **Acceptance criteria**
  - Maven project in `modern/backend` (Java 21, Spring Boot 3.3) starts with `mvn spring-boot:run` on port 8080.
  - DB path configurable (`bank.db.path`, default `../database/modernbank.db`); app fails fast with a clear message if the file is missing.
  - Global exception handler returns the JSON error contract (400/404/422/500) without stack traces.
  - CORS allows `http://localhost:5173`; `GET /api/health` returns `UP`.
  - `modern/backend/README.md` documents run and test commands; `mvn test` passes.
- **Todo:** generate project; config; `@RestControllerAdvice`; health endpoint; README.
- **Context:** `pom.xml` (legacy, reference only); `src/main/resources/db.properties`.
- **Status:** `[ ] pending`

#### B2 — [BE] SQLite data source and shared banking utilities
- **Intent:** Port the legacy connection settings and reusable rules into the service layer.
- **Acceptance criteria**
  - DataSource uses SQLite with `foreign_keys=true`, `busy_timeout=10000`, `transaction_mode=IMMEDIATE`.
  - Amount validation reproduces `BankUtil.parseAmount` (rules and messages in 2.3).
  - Account-number validation reproduces `checkAccountNumber` (1–20 alphanumeric, trimmed).
  - Money is read as `BigDecimal` scale 2 `HALF_UP`.
  - Reference generator returns `DEP`/`WDR`/`TRF` + 10 digits via `REFERENCE_SEQUENCE` inside the caller's transaction.
  - Unit tests mirror `BankUtilTest`; a DB test confirms reference uniqueness and increasing order (temp DB, never `legacybank.db`).
- **Todo:** DataSource config; `AmountValidator`; `AccountNumberValidator`; `ReferenceService`; tests with temp DB built from the legacy SQL.
- **Context:** `BankUtil.java`, `DBConnection.java`, `DatabaseIntegrationTest.java`.
- **Status:** `[ ] pending`

#### B3 — [BE] Authentication API (BCrypt + JWT)
- **Intent:** Replace plain-text login and static session with secure stateless auth.
- **Acceptance criteria**
  - `POST /api/auth/login` returns a signed JWT, employee name and username for an `ACTIVE` employee with valid password.
  - Passwords verified with BCrypt; `INACTIVE` employees, unknown users and wrong passwords all get `401` with a generic message.
  - All `/api/**` endpoints except login and health return `401` without a valid token.
  - JWT secret and expiry come from configuration/environment (a dev default is documented; no secrets committed beyond the demo default).
  - The username from the token is available to services (used for "(by username)" in descriptions).
  - Tests cover success, wrong password, inactive user, missing/expired/tampered token.
- **Todo:** Spring Security config; `EmployeeRepository`; JWT service and filter; tests.
- **Context:** `LoginFrame.java` (query and status check), `AppSession.java`.
- **Status:** `[ ] pending`

#### B4 — [BE] Customer and account lookup API
- **Intent:** Provide the data behind Customer Search and Account Details.
- **Acceptance criteria**
  - `GET /api/customers/search?customerId=` returns the customer and all their accounts ordered by account number; `?accountNumber=` finds the owner via the account.
  - Exactly one of the two parameters is required, otherwise `400`; unknown customer or account returns `404`.
  - `GET /api/accounts/{accountNumber}` returns owner name, type, status, balance (2 dp) and opened date; invalid account format returns `400`.
  - Tests cover seed customer 1001, the FROZEN and CLOSED accounts, and not-found cases.
- **Todo:** repositories, service, controller, tests.
- **Context:** `CustomerSearchFrame.java`, `AccountDetailsFrame.java`.
- **Status:** `[ ] pending`

#### B5 — [BE] Deposit API
- **Intent:** Preserve deposit rules and atomicity.
- **Acceptance criteria**
  - `POST /api/transactions/deposit` validates account number and amount with the shared rules.
  - Unknown account returns `404`; non-`ACTIVE` account returns `422` including the status.
  - Success increases the balance and inserts one `DEPOSIT` row with a `DEP` + 10-digit reference in one transaction; response returns reference and new balance.
  - Description defaults to "Cash deposit"; " (by <username>)" is always appended.
  - Any failure leaves balance and transactions unchanged (tested).
- **Todo:** service with `@Transactional`; controller; tests with temp DB.
- **Context:** `DepositFrame.java` (lines ~96–135), `BankUtil.insertTransaction/updateBalance`.
- **Status:** `[ ] pending`

#### B6 — [BE] Withdrawal API
- **Intent:** Preserve withdrawal rules, including the overdraft guard.
- **Acceptance criteria**
  - `POST /api/transactions/withdrawal` applies the same validation as deposit.
  - Insufficient funds returns `422` with the available balance in the message; balance unchanged.
  - Non-`ACTIVE` account returns `422`; unknown account `404`.
  - Success decreases the balance and inserts one `WITHDRAWAL` row with a `WDR` reference; default description "Cash withdrawal" + " (by <username>)".
  - Withdrawing the exact balance succeeds and leaves 0.00.
- **Todo:** service, controller, tests.
- **Context:** `WithdrawalFrame.java`.
- **Status:** `[ ] pending`

#### B7 — [BE] Fund transfer API
- **Intent:** Preserve the atomic two-leg transfer.
- **Acceptance criteria**
  - `POST /api/transactions/transfer` rejects identical source and destination (`422`, "Source and destination accounts must be different.").
  - Both accounts must exist (`404`, message names Source/Destination) and be `ACTIVE` (`422`); source must have sufficient funds (`422`).
  - Success debits the source and credits the destination, inserting `TRANSFER_DEBIT` ("Transfer to <dest>") and `TRANSFER_CREDIT` ("Transfer from <src>") rows with one shared `TRF` reference, plus optional note and " (by <username>)".
  - The response returns reference and both new balances.
  - If any step fails, nothing is committed (rollback test using a forced failure).
  - Total money across the two accounts is unchanged after a transfer (tested).
- **Todo:** service, controller, tests.
- **Context:** `TransferFrame.java` (lines ~95–190), `DatabaseIntegrationTest.transferIsAtomic*`.
- **Status:** `[ ] pending`

#### B8 — [BE] Transaction history API
- **Intent:** Provide the history query with filter and ordering.
- **Acceptance criteria**
  - `GET /api/accounts/{accountNumber}/transactions` returns id, type, amount, date, reference, description, ordered by `TRANSACTION_DATE DESC, TRANSACTION_ID DESC`.
  - Optional `type` accepts `DEPOSIT`, `WITHDRAWAL`, `TRANSFER_DEBIT`, `TRANSFER_CREDIT`; an invalid type returns `400`; omitted means all.
  - Invalid account format returns `400`. A valid account with no rows returns an empty list (legacy does not check account existence).
  - Tests cover ordering, filtering, and the seeded transfer pair sharing `TRF0000000006`.
- **Todo:** repository (parameterised SQL, no string concatenation of user input), controller, tests.
- **Context:** `TransactionHistoryFrame.java` (lines ~107–160).
- **Status:** `[ ] pending`

#### B9 — [BE] Dashboard summary API
- **Intent:** Supply the data for the dashboard enhancement (new, read-only).
- **Acceptance criteria**
  - `GET /api/dashboard/summary` returns `customerCount`, `accountCount`, `activeAccountCount`, `totalBalance` (2 dp), `transactionCount`, and the 5 most recent transactions (newest first).
  - Endpoint requires a valid JWT and performs no writes.
  - Seed data yields 10 customers, 15 accounts, 7 transactions (tested).
- **Todo:** aggregate queries, DTO, controller, tests.
- **Context:** `MainFrame.java` (shows no data today), schema in 2.4.
- **Status:** `[ ] pending`

#### B10 — [BE] Concurrency and atomicity test suite
- **Intent:** Verify the `BEGIN IMMEDIATE` guarantee the legacy code claims but never tests.
- **Acceptance criteria**
  - A test runs concurrent withdrawals against one account and proves the balance never goes negative and the sum of outcomes matches the final balance.
  - A test runs concurrent opposing transfers and proves no lost updates and a conserved total.
  - A test proves reference numbers stay unique under concurrent operations.
  - Tests use temp databases only; `mvn test` passes reliably (no flaky timing).
- **Todo:** write multi-threaded tests; tune busy timeout for tests if needed.
- **Context:** `DBConnection.java` (IMMEDIATE comments), `DatabaseIntegrationTest.java`.
- **Status:** `[ ] pending`

### Epic: `Frontend`
Goal: A clean, modern React + TypeScript UI built with Carbon that exposes all legacy workflows.

#### F1 — [FE] Scaffold React + TypeScript app with Carbon
- **Intent:** App skeleton with tooling and API access.
- **Acceptance criteria**
  - Vite + React 19 + TypeScript project in `modern/frontend`; `npm install` and `npm run dev` serve on `http://localhost:5173`.
  - `@carbon/react` and IBM Plex fonts are installed; global Carbon styles load; a React Router skeleton exists.
  - Typed API client reads the backend base URL from config (dev proxy or env var) and attaches the Bearer token.
  - Vitest + Testing Library configured; `npm test` and `npm run build` pass.
  - `modern/frontend/README.md` documents commands.
- **Todo:** scaffold; dependencies; router; API client; test setup; README.
- **Context:** Skills `carbon-builder`, `carbon-workflow-practices`.
- **Status:** `[ ] pending`

#### F2 — [FE] Login page and auth session
- **Intent:** Replace the Swing login with a web login and protected routes.
- **Acceptance criteria**
  - Login page (Carbon form) posts to `/api/auth/login`; shows inline error "Invalid username or password" on `401`.
  - Token is stored for the session; unauthenticated users are redirected to login; logout clears the token and returns to login.
  - A `401` from any API call logs the user out.
  - Form fields have labels, keyboard submit works, and demo credentials are shown only as a hint line on the login page.
  - Tests cover success, failure and route protection.
- **Todo:** AuthContext, login page, protected route, tests.
- **Context:** `LoginFrame.java`; Skill `carbon-accessibility-practices`.
- **Status:** `[ ] pending`

#### F3 — [FE] App shell and navigation
- **Intent:** Consistent layout replacing the Swing menu bar.
- **Acceptance criteria**
  - Carbon header shows the product name, logged-in employee name/username and a Logout action.
  - Side navigation links: Dashboard, Customer Search, Account Details, Deposit, Withdrawal, Transfer, Transaction History; the active page is highlighted.
  - Navigation is keyboard accessible and works at narrow widths (collapsible).
  - Unknown routes show a simple "not found" page.
  - Tests cover rendering and link navigation.
- **Todo:** shell layout, nav, routes, tests.
- **Context:** `MainFrame.java` menu structure (File, Customers, Transactions, Help).
- **Status:** `[ ] pending`

#### F4 — [FE] Dashboard UI enhancement
- **Intent:** The clean, modern, demo-ready dashboard (the requested UI enhancement).
- **Acceptance criteria**
  - Dashboard greets the employee and shows KPI tiles: Customers, Accounts (with active count), Total balance (formatted `#,##0.00`), Transactions, from `/api/dashboard/summary`.
  - Quick Actions tiles link to the six legacy functions with icons and short descriptions.
  - A "Recent transactions" Carbon table shows the 5 latest with colour-coded type tags.
  - Loading skeletons are shown while fetching, and an inline error notification with Retry on failure.
  - Layout uses the Carbon Grid, is responsive, and uses Carbon tokens (no ad-hoc colours).
  - Component tests cover loaded, loading and error states.
- **Todo:** KPI tile component, quick actions, table, states, minimal SCSS, tests.
- **Context:** `MainFrame.java` (current buttons), backend B9 contract; Skill `carbon-styling-practices`.
- **Status:** `[ ] pending`

#### F5 — [FE] Customer search screen
- **Intent:** Web version of Customer Search.
- **Acceptance criteria**
  - User chooses to search by Customer ID or Account Number, enters a value and submits.
  - Customer details (name, email, phone, address) and an accounts table (number, type, status tag, balance) are shown.
  - Client validation: required value; account number 1–20 alphanumeric; Customer ID numeric.
  - Backend `404`/`400` messages are displayed inline; empty state shown before the first search.
  - Account number cells link to Account Details.
  - Tests cover search success, not found and validation.
- **Todo:** form, results, service call, tests.
- **Context:** `CustomerSearchFrame.java`, backend B4.
- **Status:** `[ ] pending`

#### F6 — [FE] Account details screen
- **Intent:** Web version of Account Details.
- **Acceptance criteria**
  - Account number input shows owner, type, status tag, balance and opened date.
  - Buttons for Deposit, Withdraw and History prefill the target screen with the account number; Deposit and Withdraw are disabled when status is not `ACTIVE`.
  - Not-found and validation errors are shown inline.
  - Also reachable from the URL `/accounts/:accountNumber`.
  - Tests cover display, disabled actions for FROZEN and CLOSED, and not found.
- **Todo:** page, routing with param, tests.
- **Context:** `AccountDetailsFrame.java`, backend B4.
- **Status:** `[ ] pending`

#### F7 — [FE] Deposit screen
- **Intent:** Web version of Cash Deposit.
- **Acceptance criteria**
  - Form: account number, amount, optional description; client validation matches the legacy rules (amount > 0, max 2 decimals, max 10,000,000.00).
  - Success shows a Carbon notification with reference and new balance, and clears the amount.
  - Backend `404`/`422` messages are shown clearly (for example, frozen account).
  - Submit button is disabled while the request is in flight (no double submit).
  - Tests cover success, validation failure and a backend rejection.
- **Todo:** form component, API call, notifications, tests.
- **Context:** `DepositFrame.java`, backend B5.
- **Status:** `[ ] pending`

#### F8 — [FE] Withdrawal screen
- **Intent:** Web version of Cash Withdrawal.
- **Acceptance criteria**
  - Same form and validation as Deposit.
  - Insufficient-funds and inactive-account rejections show the backend message and leave the form data intact.
  - Success shows reference and new balance.
  - Tests cover success, insufficient funds and validation failure.
- **Todo:** reuse shared amount-form component from F7, tests.
- **Context:** `WithdrawalFrame.java`, backend B6.
- **Status:** `[ ] pending`

#### F9 — [FE] Fund transfer screen
- **Intent:** Web version of Fund Transfer.
- **Acceptance criteria**
  - Form: source account, destination account, amount, optional description; client check that source and destination differ.
  - Success shows reference and both new balances; failure shows the backend message (Source/Destination wording preserved).
  - Submit is disabled while in flight.
  - Tests cover success, same-account validation, insufficient funds and non-active destination.
- **Todo:** form, API call, notifications, tests.
- **Context:** `TransferFrame.java`, backend B7.
- **Status:** `[ ] pending`

#### F10 — [FE] Transaction history screen
- **Intent:** Web version of Transaction History.
- **Acceptance criteria**
  - Account number input plus type filter (`ALL`, `DEPOSIT`, `WITHDRAWAL`, `TRANSFER_DEBIT`, `TRANSFER_CREDIT`).
  - Table shows ID, type tag, amount, date, reference and description, newest first, with a "N transaction(s) found" count.
  - Empty result shows an empty state; prefilled account number from the URL runs the search automatically.
  - Tests cover results, filter, empty state and validation.
- **Todo:** page, table, filter, tests.
- **Context:** `TransactionHistoryFrame.java`, backend B8.
- **Status:** `[ ] pending`

---

## 7. Implementation workflow (Agent mode)

1. Complete Sub-task 0 (Issues) and get user approval.
2. Create the approved feature branch in `origin` (for example `feature/modernization`). Never touch the legacy files.
3. Implement one Story at a time in the Section 3 order: tests written and run; commit and push; comment on and close the Story Issue; update `Status` in this file; wait for user approval before the next Story.
4. Do not mark a Story complete if its tests fail.
5. After all Stories: end-to-end validation, confirm the legacy files and `legacybank.db` are unchanged, open a PR to `main` on `origin`, and do not merge it.

## 8. Risks and notes
- `init.sh` needs the `sqlite3` CLI; the README must list it as a prerequisite.
- SQLite `NUMERIC` money is stored as floating point when decimals exist; always read and round via `BigDecimal` (as the legacy does).
- SQLite allows a single writer: keep transactions short; the busy timeout comes from the legacy value.
- `TRANSACTIONS` references `ACCOUNTS.ACCOUNT_NUMBER` (string FK); keep it as is, because the schema is retained unchanged.
- The default JWT secret is for the demo only; document that it must be overridden elsewhere.
- Use synthetic demo data only.
