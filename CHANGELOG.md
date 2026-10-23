# Changelog

All notable changes to SmartSilk (Nexora). Versions follow semantic versioning; each release lists its database
migrations and configuration changes, because a deploy applies them (`docs/10-DEPLOYMENT.md` section 13).

## [1.0.0] - unreleased

First production release: the whole business on one server and the desktop app, replacing the notebooks after the
go-live import and the parallel run.

### Added

- **Sign-in and roles.** Username and password login with short-lived access tokens and rotating refresh tokens;
  account lock after 5 wrong passwords; `OWNER` (everything) and `STAFF` (read-only) roles; the first owner is
  created on the first start.
- **Parties.** Customers, suppliers, manufacturers, workers and lenders in one list, with phones, supplier credit
  days and worker rates.
- **Purchases and stock.** Purchases with receipts (partial receipts, returns), stock by product and location,
  work in progress inside and outside the business, wastage, adjustments and opening stock. Stock can never go
  negative.
- **Orders and deliveries.** Customer orders with items, deliveries from finished stock, order status derived from
  what was delivered and paid.
- **Production and outsourcing.** Batches from raw silk to finished warp with yield and wastage; jobs sent to
  manufacturers and received back with their charges.
- **Workers.** Rolling and warping work records at the rate in force on the day, and wages payable.
- **Money.** Cash and bank accounts, payments in and out allocated to the oldest bills first (or chosen by hand),
  reversals instead of edits, transfers between accounts, expenses, loans with repayments and interest, chits with monthly contributions and
  the payout, and opening receivables and payables. Cash can never go negative.
- **Reports and dashboard.** Dashboard of stock, cash, receivables and payables; customer outstanding, supplier
  payables, sales, purchases, expenses, cash and bank, inventory movements, production, outsourcing, worker payable,
  loans and chits reports, each exportable to CSV.
- **Documents.** Bills, receipts and statements uploaded and attached to the records they belong to.
- **Reminders.** Daily supplier-due reminders, cleared automatically once the bill is paid.
- **Desktop app** for Windows (MSI) and Linux (DEB) covering every screen above.
- **Operations.** Container image, production stack with automatic HTTPS, CI with tests, coverage, dependency and
  image scans, a deploy workflow with owner approval and automatic rollback, encrypted off-site backups with a
  monthly restore drill, host checks with alerts, the go-live import procedure (`deploy/import/README.md`) and the
  runbook (`docs/RUNBOOK.md`).

### Security

- Ledger tables are append-only in the database itself: the application's database account cannot update or
  delete them (privileges and triggers), on top of the rules in the code.
- Rate limits on login and on the API, request size limits, security headers, HSTS through Caddy, the app container
  read-only and without Linux capabilities, secrets only in the server's settings file.

### Database migrations

`V1` to `V16`, applied automatically on the first start:

| Migration | Content |
|---|---|
| V1-V2 | initial schema check (created and removed) |
| V3 | users, parties, roles of parties, phone numbers |
| V4 | business number sequences (`PUR-2026-0001` style) |
| V5 | permissions, roles, user roles, refresh tokens |
| V6 | material movements (stock ledger) and inventory balances |
| V7 | purchases, receipts and returns |
| V8 | customer orders and order items |
| V9 | financial accounts, payments, expenses, loans, chits, account transfers, financial transactions (money ledger) |
| V10 | opening obligations, customer and supplier payment allocations, idempotency keys, audit log |
| V11 | production batches and the order items they serve |
| V12 | outsourcing jobs, their order items, manufacturer payment allocations |
| V13 | worker work records, worker payment allocations |
| V14 | documents, notifications, notification settings |
| V15 | append-only privileges on the ledger tables |
| V16 | immutability triggers |

### Configuration

Every variable is listed with a comment in `deploy/.env.prod.example`. Required in production (the server refuses
to start without them): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `FLYWAY_USER`, `FLYWAY_PASSWORD`, `JWT_SECRET`
(at least 32 bytes). `BOOTSTRAP_OWNER_USERNAME` and `BOOTSTRAP_OWNER_PASSWORD` are set for the first start only.
