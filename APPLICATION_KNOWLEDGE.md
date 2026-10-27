# Banking Application Knowledge Base

This is the working reference for the local banking application. Update it when
the architecture, local setup, test state, or known behavior changes. Never put
passwords, SMTP keys, tokens, or other secrets in this file.

## Local development decisions

- Run PostgreSQL **locally on this computer**, not in Docker.
- The Spring Boot backend connects to `localhost:5432/bankdb`.
- Use VS Code **Terminal > Run Task** to launch each service in its own visible
  terminal. The terminal remains open so its live logs can be read.
- Do not commit `backend/.env` or copy its secret values into documentation.

## Application architecture

```text
React + Vite (127.0.0.1:5173)
             |
             | REST / JSON, /api/v1, bearer JWT
             v
Spring Boot 3.2.2 / Java 21 (127.0.0.1:8080)
             |
             | Spring Data JPA / Hibernate
             v
Local PostgreSQL 17 (localhost:5432, database bankdb)
```

The frontend API base URL is set by `frontend/.env` (`VITE_API_URL`). The
backend loads database, application URL, and SMTP values from `backend/.env`
when it starts. Do not expose the local database or development servers to the
public network.

## Repository layout

- `frontend/`: React 19, React Router, and Vite UI.
- `backend/`: Java 21 Spring Boot REST service, Spring Security/JWT, JPA, and
  PostgreSQL integration.
- `backend/src/main/java/com/bank/transaction/controller/`: REST endpoints.
- `backend/src/main/java/com/bank/transaction/service/`: authentication,
  account, and money-transfer logic.
- `backend/src/main/java/com/bank/transaction/domain/`: `User`, `Account`, and
  `Transaction` JPA entities.
- `backend/src/main/java/com/bank/transaction/repository/`: Spring Data JPA
  repositories.
- `backend/src/main/resources/application.yml`: datasource, Hibernate, SMTP,
  and verification-link configuration.
- `.vscode/tasks.json`: separate visible-terminal launch tasks for the three
  local services.

## Data model

Hibernate currently manages the application schema with `ddl-auto: update`.
The principal tables are:

- `users`: username, email, BCrypt password hash, role, linked account,
  verification state, and verification token.
- `accounts`: account ID, balance, currency, active state, and description.
- `transactions`: source/destination, amount(s), type/status, idempotency key,
  timestamp, and descriptions.

The existing PostgreSQL data directory is
`C:\Program Files\PostgreSQL\17\data`. The server runtime used on this machine
is under `%LOCALAPPDATA%\PostgreSQL\17-runtime\pgsql`. The server is configured
to listen on localhost only. The unrelated `Chinook` databases are separate
from `bankdb`; do not modify them while working on this application.

## Main API routes

All routes are under `/api/v1`:

| Route | Purpose |
| --- | --- |
| `POST /auth/register` | Self-service customer registration and verification email |
| `POST /auth/login` | Login and JWT issuance |
| `GET /auth/verify?token=...` | Verify email/account |
| `POST /auth/change-password` | Change the signed-in user's password (user taken from the JWT); returns a fresh JWT and revokes older ones |
| `POST /auth/forgot-password` | Email a 30-minute, single-use reset link to a verified customer (same reply for any email; ignores admins) |
| `POST /auth/reset-password` | Set a new customer password from a reset-link token |
| `POST /accounts` | Provision a customer account (used by admin UI) |
| `POST /admin/vault/fund` | Record an admin-authorized opening balance in `UB-0000` |
| `GET /accounts/{id}/balance` | Read an account balance |
| `POST /accounts/{id}/credit` | Credit an account from the system vault |
| `POST /transactions/transfer` | Transfer between accounts |
| `GET /transactions/quote?destinationAccountId=...&amount=...` | Get an authenticated customer's transfer quote |
| `GET /accounts/{id}/transactions` | Read account transaction history |
| `GET /admin/transactions/all` | Read the global transaction feed |
| `GET /health` | Basic service health response (public) |

## Application capabilities (current UI)

The React UI currently has these screens and workflows:

- **Login:** routes the signed-in user to the admin or customer dashboard based
  on the role in the login response.
- **Self-registration:** collects username, email, password, account ID, and
  currency (INR or USD), then asks the customer to verify via the emailed link
  before login.
- **Admin dashboard:** provisions a customer account in INR or USD, records a
  vault opening balance, credits customer accounts from the vault, and views a
  global transaction feed.
- **Customer dashboard:** displays the signed-in customer's balance and
  account history, transfers funds to a recipient account ID, and offers a
  password-change form.
- **Verification success:** displayed after following an account-verification
  link.

Money-moving operations should be performed through the UI for manual QA. The
browser uses the backend REST API behind the scenes; do not treat direct API
calls or database edits as UI testing.

### Currency support and exchange-rate behavior

INR, USD, CNY (Chinese yuan), and JPY (Japanese yen) can be selected when
registering or provisioning an account. For cross-currency transfers between
these currencies, the backend requests the
pair rate from Frankfurter (`https://api.frankfurter.dev/v2/rate/{base}/{quote}`)
and caches each pair for 15 minutes. Frankfurter supplies daily reference rates,
not continuously updated or executable market quotes. The API response's
reference date, provider, and backend fetch time are returned to the UI and
stored with each transfer. Amounts are rounded to two decimals using
`HALF_UP`. Same-currency transfers use a rate of 1 without an external call.
Only INR, USD, CNY, and JPY are supported; unsupported currency pairs fail
rather than falling back to a made-up 1:1 rate. INR, USD, and CNY amounts use
two decimal places; JPY amounts are whole yen and converted values are rounded
to the destination currency's minor-unit precision.

Exchange rates are stored in `NUMERIC(20,12)` so reciprocal rates such as
INR-to-USD are not truncated. For an existing PostgreSQL database created
before this precision change, apply
`backend/src/main/resources/db/schema-update-exchange-rate-precision.sql` once;
Hibernate's `ddl-auto: update` is not relied on to alter the existing column.
The local `bankdb` schema has been updated and verified at scale 12.

The customer UI requests a quote from the backend when recipient and amount
are entered. Confirmation sends the displayed rate and date; the backend
rechecks them against the current cached/provider rate and rejects a changed
quote before moving money. Provider/network failure fails closed; there is no
fallback to the previous hard-coded INR 83 rate. This remains demo/reference
rate functionality—not suitable for real funds, guaranteed execution, or
live trading. No API key is required for Frankfurter.

The system vault currently holds INR. Admin vault credits can fund INR, USD,
CNY, and JPY customer accounts: the admin enters an INR debit amount, reviews
the Frankfurter reference quote and converted customer credit, and confirms.
The backend rechecks cross-currency rate/date before atomically debiting the
vault and crediting the customer. Both currencies, both amounts, and the FX
metadata are recorded in the transaction ledger. This remains reference-rate
demo behavior and is not a live executable banking quote.
Provisioning or self-registration of that customer requires a unique,
accessible email address to receive account verification (and, for admin
provisioning, temporary credentials).

The customer transfer form accepts a recipient account ID and amount, not an
account directory. The backend validates that the recipient is a verified
customer and uses that account's actual currency when generating the quote.
Both source and converted amounts plus the FX rate metadata are retained in
the transaction history.

## Account creation and funding flows

There are two customer-creation paths:

1. **Self-registration** (`POST /auth/register`): the customer supplies
   username, email, password, account ID, and currency. The backend hashes the
   password, creates the account/user, and sends a verification link.
2. **Admin provisioning** (`POST /accounts`): an admin supplies username,
   email, account ID, and currency. The backend generates a temporary password
   and sends it with the verification link.

The admin dashboard records an opening balance in the vault as a
`VAULT_FUNDING` transaction from `OPENING_BALANCE` to `UB-0000`. An admin
customer credit then debits `UB-0000` and credits the customer in the same
database transaction; insufficient vault funds reject the credit. Customer
transfers derive the sender account from the authenticated customer's profile,
then debit that account and credit a different verified customer account.

At startup, `DataInitializer` creates the system admin and vault account if
`UB-0000` does not exist, using the password in `ADMIN_INITIAL_PASSWORD`
(`backend/.env`, at least 12 characters; startup fails without it). On an
existing database it replaces the retired `admin123` default with that value.
The password is never printed to the log.

## Password changes and recovery

| | Customer | Admin |
| --- | --- | --- |
| Signed in | Security tab, change password | Security tab, change password |
| Forgot password | **Forgot password?** on the login page emails a reset link to `FRONTEND_URL/reset-password?token=…` | Server-side only: set a new `ADMIN_INITIAL_PASSWORD` and `ADMIN_PASSWORD_RESET=true` in `backend/.env`, restart once, then set the flag back to `false` |

Admins are deliberately excluded from email reset: the seeded admin email is not
a real inbox, and admin recovery should require server access rather than
mailbox access. Reset tokens are 32 random bytes; only their SHA-256 hash and
expiry are stored on `users`, and a token is cleared when used.

Every password change or reset sets `users.password_changed_at`. The JWT filter
rejects tokens issued before that time and reads the role from the database, so
changing a password signs out all other sessions. The frontend treats a 401 as
an ended session and returns to the login page with a notice.

## Required money-flow invariant and current verification

The required business rule is:

- `SYSTEM_VAULT` is the sole source of customer funds. Admin-authorized credit
  moves money from the vault into a customer account and records both sides.
- Customer-to-customer movement is transfer-only: debit the authenticated
  customer's own account and credit the recipient. Customers cannot create,
  credit, or withdraw funds.
- Any initial funding of the vault must be an explicit admin-controlled and
  auditable operation; a customer-facing route must never mint money.

The backend now requires admin authorization for vault funding, customer
credits, account provisioning, and the global transaction feed. Transfers
require a customer JWT; the source account is taken from that user's profile,
not from the request body. Customers can read only their own balance/history,
while admins can inspect all account histories. Positive two-decimal amounts
and unique idempotency keys are enforced, and account rows are locked during
money movement.

The opening-balance record is an explicit local-demo funding entry, not proof
of real cash reserves. The JWT signing key (`JWT_SECRET`) and admin password
(`ADMIN_INITIAL_PASSWORD`) come from `backend/.env` or the host environment;
see `backend/.env.example`. Do not use real funds.

Configuration read from the environment: `SPRING_DATASOURCE_*`, `APP_BASE_URL`
(verification links), `FRONTEND_URL` (verify redirect), `CORS_ALLOWED_ORIGINS`
(comma-separated; defaults to the two local Vite origins), `BREVO_SMTP_KEY`,
`JWT_SECRET`, and `ADMIN_INITIAL_PASSWORD`. Real environment variables override
`.env`, which is loaded for every launch path including tests.

## Start services and view logs

Open `C:\Users\DELL\Banking Application` itself as the VS Code folder
(**File > Open Folder**) before selecting **Terminal > Run Task**. The current
VS Code session may instead be opened at `C:\Users\DELL`, which will not
discover this project's nested `.vscode/tasks.json`; keep the task file with
this application rather than adding it to the unrelated parent workspace.

Launch the tasks in this order:

1. `Database: PostgreSQL (local)` — start this first and wait until it logs that
   the database is ready.
2. `Backend: Spring Boot` — starts with the project's Java 21 runtime and
   displays Spring, Hibernate, and application logs.
3. `Frontend: Vite` — displays Vite startup and browser request/build logs.

Each task uses a new terminal so all three service logs remain visible. The
database task uses the existing local data directory; it does not create or
start a Docker container. The backend task uses `backend/.env` SMTP settings;
email sending requires an active Brevo SMTP key and matching SMTP login.

Stop each task with the terminal's **Terminate** button. Stop PostgreSQL only
after stopping the backend, so the application can close its database
connections first.

The tasks bind the HTTP services to loopback. The backend CORS allow-list
includes both `http://localhost:5173` and `http://127.0.0.1:5173` for local
development.

## Current local test state

Verified local demo accounts and balances after the 2026-10-14 INR flow:

- Admin/system vault `UB-0000`: INR 9,980,000.00
- Customer `yusuf_test`, `UB-4721`: INR 9,090.00
- Customer `wazeh`, `UB-9852`: INR 11,010.00

As of this update, local PostgreSQL, the backend, and the Vite frontend are
running on ports 5432, 8080, and 5173. The vault history has 3 entries;
`UB-4721` has 4; `UB-9852` has 3.

The completed UI flow recorded a INR 10,000,000 opening balance in the vault,
admin credits of INR 10,000 to each customer, and a customer transfer of INR
1,000 from `UB-4721` to `UB-9852`. The new customer credits each debit the
vault, and both sides of the transfer are in the transaction history.

The follow-up request to test a transfer between different currencies was
paused before creating or changing any account. At inspection time all three
accounts (admin and both customers) were INR; no USD customer exists. No
cross-currency transfer was made, and no account or balance changed during
that attempt. Creating a verified USD customer through the UI needs an
accessible unique email for verification/credentials.

Cross-currency reference-rate support is now implemented in code, but has not
yet been exercised through a live UI transfer. A verified USD customer account
is still needed to test INR-to-USD and USD-to-INR end-to-end. The rate service
and transfer behavior are covered by unit tests using a mocked provider
response.

**Legacy ledger caveat:** transaction 5 predates the vault accounting fix. It
records a INR 100 credit to `UB-4721` with the source label `SYSTEM_VAULT`, but
the old implementation did not debit `UB-0000`; that historical entry remains
unreconciled. It is visible in the global transaction feed and the customer's
history, but not in the vault account's history because its source ID is the
old label. It was intentionally not rewritten or removed. The current vault
balance reflects the new INR 10,000,000 opening entry less the two new INR
10,000 customer credits.

## Testing notes and known issues

Verified for this flow:

- Backend unit tests cover daily FX response parsing/caching, same-currency
  behavior, unsupported currency rejection, source/destination conversion,
  stored provider/date metadata, stale quote rejection, vault funding,
  balanced vault-to-customer credits, insufficient vault balance,
  authenticated sender account selection, non-positive transfer rejection,
  admin transfer rejection, and idempotency.
- Focused Maven tests passed 13 tests across the FX-rate, transfer, and account
  services. The Vite production build passed.
- The Spring application context test and frontend production build pass.
- Live local API checks confirmed unauthenticated callers receive HTTP 403 for
  credit, transfer, and account-history routes.
- Live UI transfer of INR 1,000 succeeded; admin API history queries verified
  the vault and both customer histories and final balances above.
- A requested cross-currency transfer remains untested because the existing
  customer accounts are both INR and no accessible email was supplied for a
  new verified USD customer.

Issues identified by earlier QA (negative/zero transfers, duplicate
idempotency keys, and public financial routes) have been addressed in code and
the relevant paths retested. The hard-coded JWT key and admin password have
been moved to configuration, registration no longer binds client JSON to the
`User` entity (it rejects existing account IDs, including `UB-0000`), login
returns one message for unknown users and wrong passwords, and change-password
uses the authenticated user. A successful SMTP log indicates the SMTP provider accepted the
message, not necessarily that it reached the inbox.

Email sending methods currently catch and log SMTP errors, while some UI flows
may still present success. Use the backend terminal logs plus the actual API
response and database state when checking registration/provisioning. Do not
paste credentials into chat; the test participant's temporary password was
shared during local testing and should be changed.

## Keeping this document current

When a service, database choice, schema, endpoint, startup task, test result, or
known issue changes, update the relevant section here. Record decisions and
reproducible setup guidance, but never record secret values or generated
verification tokens.
