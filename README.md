# Universal Bank — Architecture and Developer Handoff

This document is a code-oriented guide to the Universal Bank demonstration
application. It is intended to help a developer or another coding assistant
understand the repository, run it locally, trace requests through the system,
and recognize its current limitations.

> **Important:** This project is a local/demo banking application. Its displayed
> exchange rates are reference rates, its vault opening balance is an
> administrator-entered demo entry, and its current security configuration is
> not suitable for production or real funds.

## Contents

- [System at a glance](#system-at-a-glance)
- [Repository structure](#repository-structure)
- [Technology stack](#technology-stack)
- [Runtime configuration](#runtime-configuration)
- [Run locally](#run-locally)
- [Architecture and request flow](#architecture-and-request-flow)
- [Authentication and access control](#authentication-and-access-control)
- [Accounts, currencies, and database model](#accounts-currencies-and-database-model)
- [Money movement rules](#money-movement-rules)
- [Foreign exchange quotes](#foreign-exchange-quotes)
- [HTTP API reference](#http-api-reference)
- [Frontend application](#frontend-application)
- [Backend package guide](#backend-package-guide)
- [Database setup and inspection](#database-setup-and-inspection)
- [Tests and quality checks](#tests-and-quality-checks)
- [Known limitations and production work](#known-limitations-and-production-work)

## System at a glance

The application is a browser-based React client, a Spring Boot REST API, and a
local PostgreSQL database. The frontend does not connect to PostgreSQL. It
sends JSON requests to the backend, which applies authentication, validation,
business rules, and database transactions.

```text
Browser
  React 19 + Vite
  http://127.0.0.1:5173
       |
       | HTTPS in a real deployment; local development uses HTTP
       | JSON REST API, /api/v1, Authorization: Bearer <JWT>
       v
Spring Boot 3.2.2 / Java 21
  http://127.0.0.1:8080
       |
       | Spring Data JPA / Hibernate
       | pessimistic account-row locks for money movement
       v
PostgreSQL 17, local installation
  localhost:5432 / bankdb

Backend external dependencies:
  Frankfurter reference-rate API (FX quotes)
  Brevo SMTP relay (verification and provisioning email)
```

The repository’s VS Code tasks start the local PostgreSQL server, backend, and
frontend in separate visible terminals. PostgreSQL is intentionally installed
and run locally; it is not part of a Docker Compose stack.

## Repository structure

```text
Banking Application/
├── README.md                         # This developer architecture guide
├── APPLICATION_KNOWLEDGE.md          # Local workflow history and operational notes
├── .vscode/
│   └── tasks.json                    # Separate local DB, backend, and frontend tasks
├── backend/
│   ├── .env                          # Local secrets/configuration; do not commit/share
│   ├── mvnw.cmd                      # Maven wrapper for Windows
│   ├── pom.xml                       # Java/Spring dependencies and build settings
│   └── src/
│       ├── main/
│       │   ├── java/com/bank/transaction/
│       │   │   ├── config/           # Spring config, security, bootstrap initializer
│       │   │   ├── controller/       # HTTP endpoints
│       │   │   ├── domain/           # JPA entities and supported currency enum
│       │   │   ├── dto/              # Request/response transport objects
│       │   │   ├── enums/            # Roles, transaction status/type
│       │   │   ├── exception/        # Exceptions and API error mapping
│       │   │   ├── repository/       # Spring Data JPA repositories/locking queries
│       │   │   ├── security/         # JWT utility, filter, security helpers
│       │   │   ├── service/          # Authentication, accounts, FX, transactions
│       │   │   └── TransactionServiceApplication.java
│       │   └── resources/
│       │       ├── application.yml   # Server, database, mail, Hibernate, FX config
│       │       └── db/
│       │           └── schema-update-exchange-rate-precision.sql
│       └── test/java/com/bank/transaction/
│           ├── TransactionServiceApplicationTests.java
│           └── service/              # Account, transfer, and FX unit tests
└── frontend/
    ├── .env                          # Local Vite configuration; do not commit/share
    ├── package.json                  # npm scripts and React/Vite dependencies
    ├── vite.config.js
    └── src/
        ├── App.jsx                   # Routes and route guards
        ├── api.js                    # Shared fetch wrapper and API base URL
        ├── main.jsx                  # React application entry point
        ├── index.css                 # Global visual system
        ├── components/               # App shell, fields, notices, route guards, logo
        ├── lib/                      # Session storage and currency/date formatting
        └── pages/                    # Login, registration, admin/customer dashboards
```

Generated folders such as `backend/target/`, `frontend/node_modules/`, and
`frontend/dist/` are build outputs/dependencies, not application source.

## Technology stack

| Area | Technology |
| --- | --- |
| Frontend language | JavaScript with JSX, ES modules |
| UI | React 19 |
| Client-side routes | React Router 7 |
| Frontend dev/build server | Vite 7 |
| Frontend lint | ESLint 9 with React hooks/refresh rules |
| Backend language | Java 21 |
| API framework | Spring Boot 3.2.2, Spring MVC |
| Persistence | Spring Data JPA and Hibernate |
| Database | PostgreSQL 17 |
| Authentication | Spring Security with signed JWT bearer tokens |
| Password storage | Spring Security BCrypt password encoder |
| Validation | Jakarta Bean Validation |
| Email | Spring Mail using an SMTP relay |
| FX data | Frankfurter v2 reference-rate endpoint |
| Backend tests | JUnit 5, Spring Boot Test, Mockito |

The backend uses Maven through `backend/mvnw.cmd`. The frontend uses npm through
`frontend/package.json`.

## Runtime configuration

### Backend configuration

`backend/src/main/resources/application.yml` defines the application port,
database properties, Hibernate behavior, SMTP settings, JSON date handling,
and Frankfurter base URL.

`TransactionServiceApplication` loads `backend/.env` using `dotenv-java` when
the backend starts. The file is optional to the loader, but the Spring
placeholders below must be populated for the configured services to start and
work:

| Variable | Used for |
| --- | --- |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL, normally local `bankdb` |
| `SPRING_DATASOURCE_USERNAME` | Database login |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `APP_BASE_URL` | Base URL used to construct the account verification link |
| `BREVO_SMTP_KEY` | SMTP authentication secret |

Do not put actual values from `.env` in documentation, screenshots, commits, or
chat. Keep the file private and configure equivalent secrets through a proper
secret manager outside local development.

By default, `application.yml` sets Spring's server address to `0.0.0.0` on port
8080. The repository's `Backend: Spring Boot` VS Code task overrides
`SERVER_ADDRESS` to `127.0.0.1` for local use. When launching manually, bind to
loopback unless network access is intentionally required.

Hibernate is configured with `ddl-auto: update` and SQL logging enabled. This
is convenient for the local demo, but is not a production schema migration
strategy. The `exchange_rate` column has explicit precision
`NUMERIC(20,12)`. On a database created before that precision was added, apply
the provided SQL update once if the schema has not already been widened.

### Frontend configuration

`frontend/src/api.js` builds the API base from `VITE_API_URL` and appends
`/api/v1`. Set `VITE_API_URL` to the backend origin, without the `/api/v1`
suffix. For example, a local origin is `http://127.0.0.1:8080`.

Vite exposes `VITE_*` variables to browser code; never put passwords, signing
keys, SMTP credentials, or database credentials in frontend environment
variables.

### CORS

Spring Security allows local frontend origins `http://localhost:5173` and
`http://127.0.0.1:5173`, plus the currently configured hosted frontend origin
in `SecurityConfig`. The controllers also declare local CORS origins. Change
both policies deliberately when the frontend origin changes.

## Run locally

### Preferred: VS Code tasks

Open the `Banking Application` directory itself as the VS Code workspace, then
use **Terminal → Run Task**. Start the tasks in this order:

1. `Database: PostgreSQL (local)`
2. `Backend: Spring Boot`
3. `Frontend: Vite`

Each task opens a separate visible terminal to make database, backend, and
frontend logs easy to inspect. Stop the backend and frontend before stopping
PostgreSQL.

The database task uses the machine's installed PostgreSQL 17 data directory
and runtime binaries. Those paths are specific to the local Windows
environment; use your own PostgreSQL installation paths on another computer.

### Manual Windows commands

Start PostgreSQL separately first, using the local PostgreSQL 17 installation
and its existing data directory. Then start the backend in **Command Prompt**:

```cmd
cd /d "C:\Users\DELL\Banking Application\backend"
set "JAVA_HOME=C:\Users\DELL\.jdks\ms-21.0.10"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "SERVER_ADDRESS=127.0.0.1"
mvnw.cmd spring-boot:run
```

Run the frontend in another **Command Prompt**:

```cmd
cd /d "C:\Users\DELL\Banking Application\frontend"
npm run dev -- --host 127.0.0.1
```

Open `http://127.0.0.1:5173`. Keep both terminal windows open to retain the
service logs. The backend listens on port 8080 and PostgreSQL on port 5432 by
default. The backend must be started from its folder so that `backend/.env` is
found by the dotenv loader.

## Architecture and request flow

### General request path

1. A page component calls `apiRequest` in `frontend/src/api.js`.
2. The client sends JSON to the configured backend origin under `/api/v1`.
   If a session token exists, the client sends it as a bearer authorization
   header.
3. Spring Security and `JwtAuthFilter` establish the request's authenticated
   username and role.
4. A controller validates/parses the HTTP request and delegates to a service.
5. A service performs authorization-sensitive business checks and uses Spring
   Data repositories.
6. Hibernate persists the state to PostgreSQL. Money movement service methods
   use Spring transactions and lock account rows before changing balances.
7. Controllers return an `ApiResponse<T>` envelope for most endpoints. The
   frontend handles both success and API/network errors.

### Self-registration and sign-in

```text
Register page
  -> POST /api/v1/auth/register
  -> AuthController -> AuthService
  -> validate/create customer + account, BCrypt password hash
  -> create verification token; send verification email
  -> customer opens verification link
  -> GET /api/v1/auth/verify?token=...
  -> user marked verified; browser redirected to /verify-success

Login page
  -> POST /api/v1/auth/login
  -> AuthService validates verified user and BCrypt password
  -> JWT containing username subject and role, expires after one hour
  -> frontend stores token, username, account ID, role in localStorage
  -> route guard directs user to admin or customer page
```

Admin provisioning follows a similar creation path but the backend generates a
temporary password and includes it in the email with the verification link.

### Admin opening balance and vault credit

The system vault is the account `UB-0000`, initialized in INR by
`DataInitializer` if it is absent. The admin can explicitly record an opening
balance. The operation adds that amount to the vault and records a
`VAULT_FUNDING` transaction from the synthetic source `OPENING_BALANCE` to
`UB-0000`. This is an auditable demo ledger entry, not proof of external cash
reserves.

For a customer credit:

```text
Admin enters source amount (vault currency, currently INR)
  -> GET /api/v1/accounts/{customerId}/credit-quote?amount=...
  -> backend reads vault/customer currencies and requests/uses FX quote
  -> admin reviews source amount, converted amount, rate and reference date
  -> POST /api/v1/accounts/{customerId}/credit with displayed quote metadata
  -> backend locks vault and customer rows, checks quote and available funds
  -> debit vault in source currency, credit customer in destination currency
  -> persist one CREDIT transaction with both amounts/currencies and FX data
```

### Customer-to-customer payment

The customer enters a destination account ID and source-currency amount. The
backend derives the sender from the authenticated user; it does not trust the
source account ID supplied by the browser. It verifies both accounts are
eligible, obtains the pair rate, and returns a preview quote. On confirmation,
the backend checks the displayed rate/date, locks both accounts in stable
account-ID order to reduce deadlock risk, rechecks balances and account
currencies, then debits and credits in one database transaction.

The ledger row records the source amount, destination amount, currencies, rate,
provider, reference date, fetch timestamp, note/descriptions, idempotency key,
transaction type, and status.

## Authentication and access control

- Sign-in returns a JWT for a verified user.
- `JwtAuthFilter` reads `Authorization: Bearer ...`, validates the signature
  and expiry, and adds `ROLE_ADMIN` or `ROLE_CUSTOMER` to Spring's security
  context.
- Spring Security is stateless; it does not create server-side login sessions.
- Public endpoints are registration, login, email verification, and CORS
  preflight. Other routes require authentication, with role-specific rules
  shown in the API table below.
- Account balance and history controllers additionally check that a customer
  can only access their own account. Admins can inspect account histories.
- Customer transfer source account is derived from the authenticated profile.
- Passwords are BCrypt-hashed before storage.

The JWT signing key comes from `JWT_SECRET` (at least 32 bytes) and the
bootstrap admin password from `ADMIN_INITIAL_PASSWORD` (at least 12
characters). The backend refuses to start without them. Set both in
`backend/.env` locally (see `backend/.env.example`) or as host environment
variables when deployed.

## Accounts, currencies, and database model

The principal tables are `users`, `accounts`, and `transactions`. Hibernate's
naming strategy maps Java camel-case properties to PostgreSQL snake-case
columns.

### `users`

Entity: `domain/User.java`.

- Generated numeric ID
- Unique username and email
- BCrypt password hash
- Linked `account_id`
- Role (`ADMIN` or `CUSTOMER`)
- Verified flag and nullable email verification token
- `currency` is a transient registration/provisioning input, not a persisted
  user column; the account is the source of truth for currency.

### `accounts`

Entity: `domain/Account.java`.

- `account_id` string primary key, e.g. `UB-0000`
- Decimal balance
- Currency code
- Active flag
- Description / last-activity summary

The bootstrap vault is `UB-0000` and has INR currency in the current
initializer. Supported account currencies are `INR`, `USD`, `CNY`, and `JPY`.
INR, USD, and CNY use two fractional digits; JPY uses whole-yen amounts.

### `transactions`

Entity: `domain/Transaction.java`.

- Generated numeric ID
- Source and destination account IDs
- `source_amount` and destination `amount`
- Source and destination currencies
- `exchange_rate` (`NUMERIC(20,12)`), rate date, provider, and fetch timestamp
- Transaction type, status, creation time, and unique idempotency key
- Source and destination descriptions

The destination `amount` is the amount credited to the destination account;
`source_amount` is the amount debited. Same-currency events also populate
currency/rate metadata, with exchange rate `1` and provider label `Same
currency`. Earlier transactions may predate the FX columns or the balanced
vault-credit logic, so treat historical legacy rows according to when they
were created.

## Money movement rules

The intended ledger invariant is:

1. Only the system vault can fund customer accounts.
2. Vault funding is an explicit admin-controlled opening-balance operation.
3. An admin credit debits the vault and credits a customer. Cross-currency
   credits debit the vault's currency and credit the converted destination
   amount.
4. A customer can transfer only from their own authenticated account to a
   different verified customer account. Customers do not have a direct
   credit/deposit or withdrawal operation.
5. Transfers/credits require a positive amount and idempotency key.
6. A database transaction and pessimistic row locks protect each balance
   movement from concurrent updates/overspending. Customer transfer locks are
   acquired in account-ID order.

The opening-balance function remains a demo mechanism. It allows an admin to
create an initial vault balance; it does not reconcile that amount to a bank,
payment rail, or external cash account.

## Foreign exchange quotes

- Supported codes: `INR`, `USD`, `CNY`, and `JPY`.
- Provider: Frankfurter v2
  `https://api.frankfurter.dev/v2/rate/{base}/{quote}`.
- The backend—not the browser—calls the provider.
- Rates are daily reference rates, not continuously streaming, guaranteed, or
  executable bank quotes.
- Each currency pair is cached in backend memory for 15 minutes. Restarting the
  backend clears this cache.
- Same-currency quotes use a rate of `1` and do not call Frankfurter.
- Converted destination amounts are rounded using `HALF_UP` to the
  destination currency's minor-unit precision: 2 digits for INR/USD/CNY and 0
  for JPY.
- The UI submits the displayed quote rate and reference date with confirmation.
  The backend rejects cross-currency money movement if the rate or date no
  longer matches.
- The service fails closed on provider/network/invalid-response failures; it
  does not use the old hard-coded conversion value as a fallback.
- FX details are retained in the transaction row for audit/display.

## HTTP API reference

All API routes are prefixed with `/api/v1`. Most responses use:

```json
{
  "status": "SUCCESS",
  "message": "Human-readable result",
  "data": {}
}
```

The email test endpoint returns a diagnostic map rather than the standard
envelope.

| Method and route | Access | Purpose |
| --- | --- | --- |
| `POST /auth/register` | Public | Register a customer and send verification email |
| `POST /auth/login` | Public | Validate credentials and issue JWT |
| `GET /auth/verify?token=...` | Public | Verify account and redirect to frontend success page |
| `POST /auth/change-password` | Authenticated by default | Change password using username/current/new password |
| `POST /accounts` | Admin | Provision a customer account |
| `GET /accounts/{id}/balance` | Authenticated; own account or admin | Read balance and account metadata |
| `GET /accounts/{id}/transactions` | Authenticated; own account or admin | Read account history |
| `GET /accounts/{id}/credit-quote?amount=...` | Admin | Quote vault-funded customer credit |
| `POST /accounts/{id}/credit` | Admin | Credit customer from vault; accepts optional quote fields for cross-currency posting |
| `POST /admin/vault/fund` | Admin | Record opening balance in system vault |
| `GET /admin/transactions/all` | Admin | Read global transaction ledger |
| `GET /transactions/quote?destinationAccountId=...&amount=...` | Customer | Quote customer transfer |
| `POST /transactions/transfer` | Customer | Transfer from authenticated customer's own account |
| `GET /health` | Authenticated by default | Basic service health response (`UP`) |

FX quote response fields are `sourceCurrency`, `destinationCurrency`,
`sourceAmount`, `destinationAmount`, `exchangeRate`, `rateDate`, `rateProvider`,
and `fetchedAt`. Cross-currency credit/transfer requests send `quotedExchangeRate`
and `quotedExchangeRateDate` along with the requested amount and an
`idempotencyKey`.

## Frontend application

`frontend/src/App.jsx` declares the routes and wraps guest-only/authenticated
pages in `GuestOnly` and `RequireAuth` guards.

| Page | Route | Responsibility |
| --- | --- | --- |
| `pages/Login.jsx` | `/` | Sign in, display validation/API errors, store session, route by role |
| `pages/Register.jsx` | `/register` | Self-registration form including currency selection |
| `pages/VerifySuccess.jsx` | `/verify-success` | Confirmation screen after verification redirect |
| `pages/AdminDashboard.jsx` | `/admin` | Vault funding, account provisioning, vault credit quote/post, global ledger |
| `pages/CustomerDashboard.jsx` | `/customer` | Balance/account summary, transaction history, customer transfer, password change |

Shared frontend modules:

- `api.js`: shared fetch helper, bearer token attachment, response parsing, and
  network-error handling.
- `components/AppShell.jsx`: authenticated application header and navigation.
- `components/RequireAuth.jsx`: route access control based on local session
  values. This is a UX guard; the backend remains the security boundary.
- `components/Field.jsx` and `components/Notice.jsx`: reusable form field and
  feedback UI.
- `components/Wordmark.jsx`: application identity/wordmark.
- `lib/session.js`: stores/reads/removes `bank_token`, `user`, `account_id`,
  and `bank_role` in browser localStorage.
- `lib/money.js`: locale-aware display of amounts and dates, including
  whole-yen formatting and currency-specific amount input step.
- `index.css`: global visual styling and responsive layouts.

## Backend package guide

The Java base package is `com.bank.transaction`.

| Package | Main responsibility |
| --- | --- |
| `controller` | REST routing, authentication context access, request/response mapping |
| `service` | Business workflows and transactional money movement |
| `domain` | JPA entities `User`, `Account`, `Transaction`, and `CurrencyCode` |
| `dto` | Request/response objects and the `ApiResponse<T>` wrapper |
| `repository` | JPA access, idempotency queries, account locking, history queries |
| `security` | JWT creation and request filter |
| `config` | BCrypt bean, security chain, and bootstrap vault/admin initialization |
| `exception` | Domain exceptions and centralized API error handling |
| `enums` | User role and transaction type/status values |

Important service responsibilities:

- `AuthService`: login, user/customer creation, BCrypt, verification token and
  email, password change.
- `AccountService`: bootstrap/account creation, vault funding, admin
  credit/quote, account balance helpers.
- `TransactionService`: customer transfer/quote, recipient/sender validation,
  row-lock ordering, balance checks, transaction history.
- `ExchangeRateService`: supported-currency validation, Frankfurter HTTP
  client, response validation, 15-minute in-memory pair cache.

## Database setup and inspection

The local application database is PostgreSQL `bankdb`, normally on port 5432.
The repository's PostgreSQL task launches the existing local PostgreSQL 17
installation and data directory; do not start Docker for this setup.

Example `psql` connection from Windows Command Prompt (replace the username
with the value configured for your local database):

```cmd
psql -h localhost -p 5432 -U bank_user -d bankdb
```

Useful SQL:

```sql
\dt
\d users
\d accounts
\d transactions

SELECT account_id, balance, currency, active FROM accounts ORDER BY account_id;
SELECT id, type, status, source_account_id, destination_account_id,
       source_amount, amount, source_currency, destination_currency,
       exchange_rate, exchange_rate_date, created_at
FROM transactions
ORDER BY created_at DESC;
```

Use application services/UI for money movement; do not edit balances directly
for functional testing. Direct SQL is appropriate for read-only inspection or
carefully planned administrative recovery.

## Tests and quality checks

Run the backend tests from `backend/` with Java 21 and the local environment
configured:

```cmd
mvnw.cmd test
```

Focused suites:

```cmd
mvnw.cmd "-Dtest=ExchangeRateServiceTest,TransactionServiceTest,AccountServiceTest" test
```

Run frontend checks from `frontend/`:

```cmd
npm run lint
npm run build
```

Existing backend tests cover:

- Frankfurter response parsing, currency validation, same-currency behavior,
  and pair caching.
- Customer transfer balance changes, cross-currency conversion, FX metadata,
  and stale quote rejection.
- Admin vault credits, currency conversion, quote generation/stale quote
  rejection, insufficient balance, and transaction recording.
- Account/vault funding and idempotency behavior.
- Spring application context startup.

Tests using mocked provider responses verify application behavior without
depending on Frankfurter availability. They do not constitute a live UI test
against a real second-currency account.

## Known limitations and production work

1. **Not a real bank or payment rail.** No external settlement, regulatory
   controls, reconciliation, KYC, limits, dispute handling, or audit-grade
   operational controls are implemented.
2. **Demo funding.** Admin can record an opening balance in the vault. That
   action creates a ledger event; it does not verify any external source of
   funds.
3. **Reference FX only.** Frankfurter rates are daily reference values, can be
   unavailable, and are not executable guaranteed customer rates.
4. **Signing key rotation.** The JWT key now comes from `JWT_SECRET`, but there
   is no key rotation; changing it signs every user out.
5. **Bootstrap credentials.** The admin password comes from
   `ADMIN_INITIAL_PASSWORD`. There is no forced change on first login and no
   admin password-change screen yet.
6. **Email failure handling.** Some account email paths log SMTP errors and
   continue, so an API/UI success message does not prove the email reached the
   recipient. Verify SMTP logs and actual delivery during QA.
7. **Deployment URLs.** The verify redirect uses `FRONTEND_URL`, email links use
   `APP_BASE_URL`, and allowed browser origins use `CORS_ALLOWED_ORIGINS`; set
   all three for a deployed environment.
8. **Schema management.** `ddl-auto: update` is enabled for local convenience.
   Adopt explicit versioned migrations and review existing data before schema
   changes outside local development.
9. **Frontend session storage.** JWTs live in localStorage, making XSS
   prevention important. Consider a hardened cookie/session design for a
   production application.
10. **Deployment/network exposure.** The YAML default binds to all interfaces;
    use loopback locally and configure a firewall, TLS reverse proxy, secure
    origins, rate limits, monitoring, and secrets before network exposure.
11. **Historical ledger rows.** Older records may predate current vault
    balancing and FX metadata. Do not rewrite history automatically; reconcile
    such rows through a documented accounting procedure.

## Living documentation

`APPLICATION_KNOWLEDGE.md` keeps machine-specific local setup notes, prior QA
observations, and operational history. This README describes the current
code-oriented architecture. Update both when a material architecture or local
workflow changes, and do not add secrets or passwords to either file.
