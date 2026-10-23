# Go-live data import

The system's history starts on the go-live date. Everything before it is entered as **opening records** through the
normal API, never as invented purchases, sales or payments, so the ledger rules hold from the first day. All calls
below are made by the owner (or with the owner's token) against `https://<APP_DOMAIN>/api/v1`; every request body is
validated exactly as in daily use.

Before starting, read `docs/10-DEPLOYMENT.md` section 12 (the business rules) and keep the notebook, the stock count
sheet and the bank statement at hand.

## 1. Plan

| When | What |
|---|---|
| A week before | Collect party lists, count sheet template, statements. Agree the go-live date with the owner. |
| Go-live evening | **Freeze**: no new notebook entries for stock, accounts, receivables, payables, loans or chits until sign-off. |
| Go-live evening | Rehearsal on a restored copy (section 2), then the real import on production (section 3). |
| Same evening | Reconciliation report (section 4), owner sign-off, `golive-baseline` backup (section 5). |

## 2. Rehearsal on a copy

Run the whole import once on a copy of production, never on production first:

```bash
cd /opt/nexora/deploy
./scripts/backup.sh --label manual        # current production state
# then, on a separate machine with the off-site read credential and the offline key:
OWNER_USERNAME=... OWNER_PASSWORD=... ./scripts/restore-drill.sh --from <remote>:nexora --key ~/age.key \
    --image ghcr.io/<owner>/nexora:<tag> --label manual --keep
```

`--keep` leaves the restored copy running on `http://127.0.0.1:18080`; point the calls below at it. Fix every rejected row in the notebook figures, then repeat on production. Throw the
rehearsal copy away afterwards: `docker rm -f drill-app drill-pg && docker network rm nexora-drill`.

## 3. Import order

Each step needs the ids returned by the previous ones; keep a sheet of `notebook name -> id`.

1. **Parties** - `POST /parties` per customer, supplier, manufacturer, worker and lender, with roles, phone,
   `defaultCreditDays` for suppliers and worker rates. Print `GET /parties` and let the owner tick every name.
2. **Opening stock** - `POST /inventory/opening-balances`, one per product and internal location, counted on
   the go-live day:
   `{"productType":"RAW_SILK","location":"RAW_STOCK","quantityKg":120.000,"movementDate":"<go-live date>","notes":"count sheet"}`.
   Silk with a manufacturer is `EXTERNAL_WIP` and a batch on the machine `INTERNAL_WIP`, with a note naming the
   job. A second opening for the same product and location is refused (`409 DUPLICATE_RESOURCE`); a wrong figure
   is corrected afterwards with an adjustment (`POST /inventory/adjustments`), never by entering it again.
3. **Accounts** - `POST /finance/accounts` per cash drawer and bank account with `openingBalance` (counted cash or
   statement balance) and `openingDate` = go-live date.
4. **Receivables and payables** - `POST /opening-obligations`, one per party per original bill, dated with the
   bill's own date so that payments settle the oldest first:
   `{"purpose":"CUSTOMER","partyId":"<id>","obligationDate":"2026-03-14","originalAmount":25000.00,"referenceNote":"ledger p.12"}`.
   Purposes: `CUSTOMER` (owed to the business), `SUPPLIER` (with `dueDate` = bill date + credit days),
   `MANUFACTURER`, `WORKER`. Opening obligations cannot be edited or deleted: enter each one exactly once.
5. **Loans** - `POST /loans/existing` per running loan with the original `principalAmount` and `startDate` and the
   `outstandingPrincipal` today. No money moves: the loan money is already inside the account balances.
6. **Chits** - `POST /chits` per running chit with `priorContributionsCount` and `priorContributionsAmount` paid
   before go-live. No money moves.

## 4. Reconciliation report

Compare every figure with the notebook and the count sheet. A difference above Rs 1.00 or 0.001 kg blocks go-live
until it is explained and corrected.

| Figure | Where |
|---|---|
| Stock per product and location | `GET /inventory/summary`, `GET /inventory/movements?movementType=OPENING_BALANCE` |
| Balance per account | `GET /finance/accounts` |
| Owed by each customer | `GET /customers/{id}/outstanding`, `GET /reports/customer-outstanding` |
| Owed to each supplier | `GET /suppliers/{id}/payables`, `GET /reports/supplier-payables` |
| Totals, manufacturer and worker payables, loans | `GET /dashboard/summary` |
| Chits paid so far | `GET /chits` |

Then confirm on the database that the opening data holds together and created nothing else:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T postgres \
  psql -U nexora_owner -d nexora_db -v ON_ERROR_STOP=1 < scripts/reconciliation.sql   # every query returns 0 rows
docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T postgres psql -U nexora_owner -d nexora_db -c \
  "select (select count(*) from purchases) purchases, (select count(*) from expenses) expenses,
          (select count(*) from financial_transactions where transaction_type <> 'OPENING_BALANCE') other_money,
          (select count(*) from material_movements where movement_type <> 'OPENING_BALANCE') other_stock"
                                                                                       # all four are 0
```

The same import and checks run in the test suite (`GoLiveImportIntegrationTest`).

## 5. Sign-off

1. The owner signs the sign-off sheet: date, every figure of section 4, signature. Keep it with the notebook.
2. Take the baseline backup and keep it forever:
   ```bash
   ./scripts/backup.sh --label golive-baseline
   ```
   Copy `backups/nexora-golive-baseline-*` to a second place; the remote bucket's 30-day lifecycle does not apply
   to a copy kept offline.
3. End the freeze. The parallel run (notebook and system side by side, `docs/10-DEPLOYMENT.md` section 12.4) starts
   the next working day.
