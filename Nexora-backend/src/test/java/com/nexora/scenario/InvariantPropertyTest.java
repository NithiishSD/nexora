package com.nexora.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.support.AbstractApiTest;
import com.nexora.support.DatabaseCleaner;

// NFR-REL-01: random sequences of money and stock operations, many of them meant to be refused (too large, already
// reversed, wrong state), must never break an invariant. Every operation goes through the API exactly as a client sends
// it; after each one the database is checked. Fixed seeds keep a failure reproducible (the message names seed and step).
class InvariantPropertyTest extends AbstractApiTest {

    private static final long[] SEEDS = {7L, 42L, 2026L};
    private static final int STEPS = 120;

    private Random random;
    private final List<String> accounts = new ArrayList<>();
    private final List<String> cashAccounts = new ArrayList<>();
    private final List<String> customers = new ArrayList<>();
    private final List<String> expenses = new ArrayList<>();
    private final List<String> payments = new ArrayList<>();
    private final List<String> batches = new ArrayList<>();
    private String supplier;

    @Test
    @DisplayName("NFR-REL-01 random operation sequences keep all invariants")
    void randomOperationSequences_keepAllInvariants() throws Exception {
        for (long seed : SEEDS) {
            DatabaseCleaner.clean(jdbc);
            setUp(seed);
            for (int step = 0; step < STEPS; step++) {
                String op = operate();
                assertInvariants("seed " + seed + " step " + step + " (" + op + ")");
            }
            // the sequence really wrote things of every kind, not only refusals
            for (String table : List.of("expenses", "account_transfers", "payments", "material_movements")) {
                assertThat(count("select count(*) from " + table)).as("seed %d: rows in %s", seed, table).isPositive();
            }
            assertThat(count("select count(*) from financial_transactions where transaction_type = 'REVERSAL'"))
                    .as("seed %d: reversals", seed).isPositive();
            assertThat(count("select count(*) from production_batches where status = 'COMPLETED'"))
                    .as("seed %d: completed batches", seed).isPositive();
        }
    }

    private void setUp(long seed) throws Exception {
        random = new Random(seed);
        List.of(accounts, cashAccounts, customers, expenses, payments, batches).forEach(List::clear);
        cashAccounts.add(createAccount("Cash", "CASH", "50000.00"));
        accounts.add(cashAccounts.getFirst());
        accounts.add(createAccount("SBI Current", "BANK", "100000.00"));
        accounts.add(createAccount("Canara Savings", "BANK", "20000.00"));
        supplier = data.supplier().id().toString();
        String purchase = createPurchase(UUID.fromString(supplier), "RAW_SILK", "300.000", "800.00");
        receive(purchase, "300.000", "300.000", "0.000");
        for (int i = 0; i < 2; i++) {
            UUID customer = data.customer().id();
            customers.add(customer.toString());
            confirmedOrder(customer, "RAW_SILK", "10.000", "1000.00");
        }
    }

    // One random operation; refusals (4xx) are part of the test, only a 5xx is a failure.
    private String operate() throws Exception {
        int choice = random.nextInt(10);
        int status;
        String op;
        switch (choice) {
            case 0 -> {
                String account = pick(accounts);
                boolean cash = cashAccounts.contains(account);
                op = "expense";
                status = post("/api/v1/expenses", """
                        {"category":"TRANSPORT","amount":%s,"expenseDate":"%s","financialAccountId":"%s",
                         "paymentMethod":"%s"}""".formatted(money(40000), today(), account, cash ? "CASH" : "UPI"),
                        expenses);
            }
            case 1 -> {
                op = "expense reverse";
                status = expenses.isEmpty() ? 200 : post("/api/v1/expenses/" + pick(expenses) + "/reverse",
                        "{\"reason\":\"random\"}", null);
            }
            case 2 -> {
                String from = pick(accounts);
                String to = pick(accounts);
                op = "transfer";
                status = post("/api/v1/finance/transfers", """
                        {"sourceAccountId":"%s","destinationAccountId":"%s","amount":%s,"transferDate":"%s"}"""
                        .formatted(from, to, money(60000), today()), null);
            }
            case 3 -> {
                op = "adjustment";
                status = post("/api/v1/finance/accounts/" + pick(accounts) + "/adjustments", """
                        {"direction":"%s","amount":%s,"transactionDate":"%s","reason":"count"}"""
                        .formatted(random.nextBoolean() ? "IN" : "OUT", money(30000), today()), null);
            }
            case 4 -> {
                String account = pick(accounts);
                op = "customer payment";
                status = post("/api/v1/payments", """
                        {"purpose":"CUSTOMER","partyId":"%s","amount":%s,"paymentMethod":"%s","financialAccountId":"%s"}"""
                        .formatted(pick(customers), money(15000), cashAccounts.contains(account) ? "CASH" : "UPI",
                                account), payments);
            }
            case 5 -> {
                String account = pick(accounts);
                op = "supplier payment";
                status = post("/api/v1/payments", """
                        {"purpose":"SUPPLIER","partyId":"%s","amount":%s,"paymentMethod":"%s","financialAccountId":"%s"}"""
                        .formatted(supplier, money(90000), cashAccounts.contains(account) ? "CASH" : "BANK_TRANSFER",
                                account), payments);
            }
            case 6 -> {
                op = "payment reverse";
                status = payments.isEmpty() ? 200 : post("/api/v1/payments/" + pick(payments) + "/reverse",
                        "{\"reason\":\"random\"}", null);
            }
            case 7 -> {
                op = "wastage";
                status = post("/api/v1/inventory/wastage", """
                        {"productType":"RAW_SILK","fromLocation":"RAW_STOCK","quantityKg":%s,"movementDate":"%s",
                         "reason":"damp"}""".formatted(kg(40), today()), null);
            }
            case 8 -> {
                op = "batch plan and start";
                String id = body(asOwner(postJson("/api/v1/production-batches", """
                        {"productType":"KORA_WARP","productionDate":"%s","inputWeightKg":%s}""".formatted(today(),
                        kg(120))))).get("id").asString();
                status = asOwner(postJson("/api/v1/production-batches/" + id + "/start", "{}")).andReturn()
                        .getResponse().getStatus();
                if (status == 200) {
                    batches.add(id);
                }
            }
            default -> {
                op = "batch complete";
                if (batches.isEmpty()) {
                    status = 200;
                } else {
                    String id = batches.remove(random.nextInt(batches.size()));
                    BigDecimal input = body(asOwner(get("/api/v1/production-batches/" + id))).get("inputWeightKg")
                            .decimalValue();
                    BigDecimal output = input.multiply(BigDecimal.valueOf(90 + random.nextInt(10)))
                            .divide(BigDecimal.valueOf(100), 3, RoundingMode.DOWN);
                    // sometimes deliberately unreconciled (422), otherwise the rest is wastage
                    BigDecimal wastage = random.nextInt(4) == 0 ? BigDecimal.ONE : input.subtract(output);
                    status = asOwner(postJson("/api/v1/production-batches/" + id + "/complete", """
                            {"outputWeightKg":%s,"wastageWeightKg":%s,"discrepancyWeightKg":0.000}"""
                            .formatted(output, wastage))).andReturn().getResponse().getStatus();
                    if (status != 200) {
                        batches.add(id);
                    }
                }
            }
        }
        assertThat(status).as(op).isLessThan(500);
        return op;
    }

    private void assertInvariants(String where) {
        // stock never negative, and the projection equals the movement ledger
        assertThat(count("select count(*) from inventory_balances where quantity_kg < 0")).as(where).isZero();
        assertThat(count("""
                select count(*) from inventory_balances b where b.quantity_kg <> coalesce((
                  select sum(case when m.to_location = b.location and m.to_product_type = b.product_type
                                  then m.quantity_kg else 0 end)
                       - sum(case when m.from_location = b.location and m.product_type = b.product_type
                                  then m.quantity_kg else 0 end) from material_movements m), 0)""")).as(where).isZero();
        // money never negative in any account
        assertThat(count("""
                select count(*) from (select financial_account_id,
                  sum(case when direction = 'IN' then amount else -amount end) as balance
                  from financial_transactions group by financial_account_id) x where balance < 0""")).as(where).isZero();
        // a reversal mirrors its original: same account and amount, opposite direction; reversed records say so
        assertThat(count("""
                select count(*) from financial_transactions r join financial_transactions o
                  on o.id = r.reverses_transaction_id
                where r.amount <> o.amount or r.direction = o.direction
                   or r.financial_account_id <> o.financial_account_id""")).as(where).isZero();
        assertThat(count("""
                select count(*) from expenses e where (e.status = 'REVERSED') <> exists (select 1 from
                  financial_transactions t where t.expense_id = e.id and t.reverses_transaction_id is not null)"""))
                .as(where).isZero();
        // allocations of active payments never exceed the payment or the obligation
        assertThat(count("""
                select count(*) from payments p where p.amount < coalesce((select sum(a.allocated_amount) from (
                  select payment_id, allocated_amount from customer_payment_allocations union all
                  select payment_id, allocated_amount from supplier_payment_allocations) a
                  where a.payment_id = p.id), 0)""")).as(where).isZero();
        assertThat(count("""
                select count(*) from order_items i where i.amount < coalesce((select sum(a.allocated_amount)
                  from customer_payment_allocations a join payments p on p.id = a.payment_id and p.status = 'RECORDED'
                  where a.order_item_id = i.id), 0)""")).as(where).isZero();
        assertThat(count("""
                select count(*) from purchase_payables pp where pp.payable < coalesce((select sum(a.allocated_amount)
                  from supplier_payment_allocations a join payments p on p.id = a.payment_id and p.status = 'RECORDED'
                  where a.purchase_id = pp.purchase_id), 0)""")).as(where).isZero();
        // a completed batch is reconciled
        assertThat(count("""
                select count(*) from production_batches where status = 'COMPLETED'
                  and input_weight_kg <> output_weight_kg + wastage_weight_kg + discrepancy_weight_kg"""))
                .as(where).isZero();
    }

    // POSTs with a fresh key; remembers the new id when the write succeeded and a list is given.
    private int post(String path, String json, List<String> created) throws Exception {
        var response = asOwner(postWithKey(path, json)).andReturn().getResponse();
        if (created != null && response.getStatus() == 201) {
            created.add(jsonMapper.readTree(response.getContentAsString()).get("id").asString());
        }
        return response.getStatus();
    }

    private <T> T pick(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    // 1.00 .. max.00 in whole rupees plus random paise
    private String money(int max) {
        return BigDecimal.valueOf(1 + random.nextInt(max)).add(BigDecimal.valueOf(random.nextInt(100), 2))
                .toPlainString();
    }

    private String kg(int max) {
        return BigDecimal.valueOf(1 + random.nextInt(max)).add(BigDecimal.valueOf(random.nextInt(1000), 3))
                .toPlainString();
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
