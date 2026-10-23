package com.nexora.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.nexora.finance.application.FinanceService;
import com.nexora.inventory.application.InventoryService;
import com.nexora.support.AbstractIntegrationTest;
import com.nexora.support.DatabaseCleaner;

// NFR-PERF-01..06 on the five-year reference workload, through real HTTP on a random port (08-TESTING section 12):
// 20 warm-up requests, then the measured ones; p95 from the sorted durations. Tagged: runs only with -Pperformance.
@Tag("performance")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PerformanceSmokeTest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    @Value("${nexora.bootstrap.owner-username}")
    String username;

    @Value("${nexora.bootstrap.owner-password}")
    String password;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    InventoryService inventory;

    @Autowired
    FinanceService finance;

    @Autowired
    PlatformTransactionManager transactions;

    @Autowired
    Clock clock;

    private PerfClient api;
    private ReferenceDataGenerator.Result data;
    private LocalDate today;

    @BeforeAll
    void referenceWorkload() {
        DatabaseCleaner.clean(jdbc);
        today = LocalDate.now(clock);
        api = new PerfClient(port);
        api.login(username, password);
        data = new ReferenceDataGenerator(api, inventory, finance, transactions, today).generate();
        System.out.println("PERF reference workload generated in " + data.took().toSeconds() + " s");
    }

    @Test
    @DisplayName("NFR-PERF-01 reference dataset generated with consistent balances")
    void referenceDataset_generatedWithConsistentBalances() {
        long movements = count("select count(*) from material_movements");
        long ledger = count("select count(*) from financial_transactions");
        System.out.println("PERF movements=" + movements + " transactions=" + ledger);
        assertThat(movements).isGreaterThanOrEqualTo(100_000);
        assertThat(ledger).isGreaterThanOrEqualTo(100_000);
        assertThat(data.took()).isLessThanOrEqualTo(Duration.ofMinutes(10));
        assertConsistent();
    }

    @Test
    @DisplayName("NFR-PERF-02 reads within p95 of300ms")
    void reads_withinP95Of300ms() {
        for (String path : List.of("/api/v1/parties/" + data.customers().getFirst(), "/api/v1/purchases?size=20",
                "/api/v1/orders?size=20", "/api/v1/finance/transactions?size=20", "/api/v1/inventory/summary")) {
            assertThat(p95(path, 200)).as(path).isLessThanOrEqualTo(300);
        }
    }

    @Test
    @DisplayName("NFR-PERF-03 dashboard within p95 of500ms")
    void dashboard_withinP95Of500ms() {
        assertThat(p95("/api/v1/dashboard/summary", 100)).isLessThanOrEqualTo(500);
    }

    @Test
    @DisplayName("NFR-PERF-04 writes within p95 of500ms")
    void writes_withinP95Of500ms() {
        String supplier = data.suppliers().getFirst();
        String bank = data.banks().getFirst();
        List<Long> creates = new ArrayList<>();
        List<Long> receipts = new ArrayList<>();
        List<Long> deliveries = new ArrayList<>();
        List<Long> payments = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            boolean measured = i >= 20;
            PerfClient.Response created = api.send("POST", "/api/v1/purchases", """
                    {"supplierId":"%s","purchaseDate":"%s","orderedWeightKg":10.000,"ratePerKg":800.00,
                     "paymentType":"CREDIT"}""".formatted(supplier, today), true);
            String purchase = id(created.body());
            PerfClient.Response received = api.send("POST", "/api/v1/purchases/" + purchase + "/receipts", """
                    {"receiptDate":"%s","receivedWeightKg":10.000,"acceptedWeightKg":10.000,"rejectedWeightKg":0.000}"""
                    .formatted(today), true);
            String order = id(api.send("POST", "/api/v1/orders", """
                    {"customerId":"%s","orderDate":"%s",
                     "items":[{"productType":"RAW_SILK","requiredWeightKg":1.000,"ratePerKg":1000.00}]}"""
                    .formatted(data.customers().get(i % data.customers().size()), today), true).body());
            api.post("/api/v1/orders/" + order + "/confirm", "");
            api.post("/api/v1/orders/" + order + "/ready", "");
            PerfClient.Response delivered = api.send("POST", "/api/v1/orders/" + order + "/deliver",
                    "{\"deliveryDate\":\"" + today + "\"}", true);
            PerfClient.Response paid = api.send("POST", "/api/v1/payments", """
                    {"purpose":"SUPPLIER","partyId":"%s","amount":100.00,"paymentMethod":"BANK_TRANSFER",
                     "financialAccountId":"%s"}""".formatted(supplier, bank), true);
            for (PerfClient.Response r : List.of(created, received, delivered, paid)) {
                assertThat(r.status()).as(r.body()).isIn(200, 201);
            }
            if (measured) {
                creates.add(created.nanos());
                receipts.add(received.nanos());
                deliveries.add(delivered.nanos());
                payments.add(paid.nanos());
            }
        }
        report("create purchase", creates, 500);
        report("receive", receipts, 500);
        report("deliver", deliveries, 500);
        report("payment", payments, 500);

        // AC1: 100 payments, each settling 20 obligations (oldest first) -> p95 <= 800 ms
        String bulk = id(api.post("/api/v1/parties", "{\"name\":\"Bulk Customer\",\"roles\":[\"CUSTOMER\"]}").toString());
        for (int i = 0; i < 2400; i++) {
            String order = id(api.post("/api/v1/orders", """
                    {"customerId":"%s","orderDate":"%s",
                     "items":[{"productType":"RAW_SILK","requiredWeightKg":1.000,"ratePerKg":100.00}]}"""
                    .formatted(bulk, today)).toString());
            api.post("/api/v1/orders/" + order + "/confirm", "");
        }
        List<Long> wide = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            PerfClient.Response r = api.send("POST", "/api/v1/payments", """
                    {"purpose":"CUSTOMER","partyId":"%s","amount":2000.00,"paymentMethod":"UPI",
                     "financialAccountId":"%s"}""".formatted(bulk, bank), true);
            assertThat(r.status()).as(r.body()).isEqualTo(201);
            assertThat(r.body()).contains("\"allocatedAmount\":2000.00");
            if (i >= 20) {
                wide.add(r.nanos());
            }
        }
        assertThat(count("select count(*) from customer_payment_allocations a join payments p on p.id = a.payment_id"
                + " where p.party_id = '" + bulk + "'")).isEqualTo(120 * 20);
        report("payment over 20 obligations", wide, 800);
    }

    @Test
    @DisplayName("NFR-PERF-05 twenty clients mixed load no unexpected failures")
    void twentyClientsMixedLoad_noUnexpectedFailures() throws Exception {
        Map<String, AtomicInteger> outcomes = new ConcurrentHashMap<>();
        List<String> unexpected = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Future<?>> clients = new ArrayList<>();
        for (int t = 0; t < 20; t++) {
            int client = t;
            clients.add(pool.submit(() -> {
                PerfClient own = new PerfClient(port);
                own.login(username, password);
                for (int i = 0; i < 100; i++) {
                    PerfClient.Response r = switch ((client + i) % 6) {
                        case 0 -> own.send("GET", "/api/v1/dashboard/summary", null, false);
                        case 1 -> own.send("GET", "/api/v1/orders?size=20", null, false);
                        case 2 -> own.send("POST", "/api/v1/expenses", """
                                {"category":"OTHER","expenseKind":"BUSINESS","amount":10.00,"expenseDate":"%s",
                                 "financialAccountId":"%s","paymentMethod":"CASH"}""".formatted(today, data.cash()), true);
                        case 3 -> own.send("POST", "/api/v1/payments", """
                                {"purpose":"CUSTOMER","partyId":"%s","amount":100.00,"paymentMethod":"UPI",
                                 "financialAccountId":"%s"}""".formatted(data.customers().get(client),
                                data.banks().get(client % 3)), true);
                        case 4 -> own.send("POST", "/api/v1/inventory/wastage", """
                                {"productType":"RAW_SILK","fromLocation":"RAW_STOCK","quantityKg":0.100,
                                 "movementDate":"%s","reason":"load test"}""".formatted(today), true);
                        default -> own.send("POST", "/api/v1/payments", """
                                {"purpose":"SUPPLIER","partyId":"%s","amount":10.00,"paymentMethod":"BANK_TRANSFER",
                                 "financialAccountId":"%s"}""".formatted(data.suppliers().get(client % 20),
                                data.banks().get(client % 3)), true);
                    };
                    outcomes.computeIfAbsent(String.valueOf(r.status()), k -> new AtomicInteger()).incrementAndGet();
                    boolean businessConflict = r.status() == 409 && (r.body().contains("INSUFFICIENT_FUNDS")
                            || r.body().contains("INSUFFICIENT_INVENTORY"));
                    if (r.status() / 100 != 2 && !businessConflict) {
                        unexpected.add(r.status() + " " + r.body());
                    }
                }
                return null;
            }));
        }
        for (Future<?> client : clients) {
            client.get();
        }
        pool.shutdown();
        System.out.println("PERF mixed load outcomes " + outcomes);
        // AC1: no 500, no deadlock or lock timeout, every invariant still holds
        assertThat(unexpected).isEmpty();
        assertConsistent();
    }

    @Test
    @DisplayName("NFR-PERF-06 year report and csv export within budgets")
    void yearReportAndCsvExport_withinBudgets() {
        String year = "from=" + today.minusYears(1) + "&to=" + today;
        // AC1: one-year reports, 20 measured runs each, p95 <= 2 s
        for (String report : List.of("/api/v1/reports/sales?" + year, "/api/v1/reports/purchases?" + year,
                "/api/v1/reports/cash-bank?" + year, "/api/v1/reports/expenses?" + year,
                "/api/v1/reports/inventory-movements?productType=VUDA_WARP&" + year,
                "/api/v1/reports/supplier-payables", "/api/v1/reports/customer-outstanding")) {
            List<Long> runs = measure(() -> api.send("GET", report, null, false), 5, 20);
            report(report, runs, 2000);
        }
        // AC2: a CSV export of (just under) 50000 rows within 10 s
        LocalDate from = jdbc.queryForObject("select movement_date from material_movements"
                + " order by movement_date desc offset 49999 limit 1", LocalDate.class).plusDays(1);
        long rows = count("select count(*) from material_movements where movement_date >= '" + from + "'");
        PerfClient.Response csv = api.send("GET", "/api/v1/inventory/movements?format=csv&from=" + from + "&to=" + today,
                null, false);
        System.out.println("PERF csv rows=" + rows + " ms=" + csv.nanos() / 1_000_000);
        assertThat(csv.status()).as(csv.body().substring(0, Math.min(300, csv.body().length()))).isEqualTo(200);
        assertThat(csv.body().split("\r\n")).hasSize((int) rows + 1);
        assertThat(rows).isGreaterThan(40_000);
        assertThat(Duration.ofNanos(csv.nanos())).isLessThanOrEqualTo(Duration.ofSeconds(10));
    }

    // ---- helpers ----

    private long p95(String path, int measured) {
        List<Long> runs = measure(() -> api.send("GET", path, null, false), 20, measured);
        long p95 = PerfClient.p95Millis(runs);
        System.out.println("PERF " + path + " p95=" + p95 + " ms");
        return p95;
    }

    private static List<Long> measure(Supplier<PerfClient.Response> call, int warmUp, int measured) {
        List<Long> runs = new ArrayList<>();
        for (int i = 0; i < warmUp + measured; i++) {
            PerfClient.Response r = call.get();
            assertThat(r.status()).as(r.body()).isEqualTo(200);
            if (i >= warmUp) {
                runs.add(r.nanos());
            }
        }
        return runs;
    }

    private static void report(String what, List<Long> runs, long budgetMillis) {
        long p95 = PerfClient.p95Millis(runs);
        System.out.println("PERF " + what + " p95=" + p95 + " ms (budget " + budgetMillis + ")");
        assertThat(p95).as(what).isLessThanOrEqualTo(budgetMillis);
    }

    // NFR-REL-01 invariants after load: no negative stock or money, the stock projection equals the movement ledger.
    private void assertConsistent() {
        assertThat(count("select count(*) from inventory_balances where quantity_kg < 0")).isZero();
        assertThat(count("""
                select count(*) from inventory_balances b where b.quantity_kg <> coalesce((
                    select sum(case when m.to_location = b.location and m.to_product_type = b.product_type
                                    then m.quantity_kg else 0 end)
                         - sum(case when m.from_location = b.location and m.product_type = b.product_type
                                    then m.quantity_kg else 0 end)
                    from material_movements m), 0)""")).isZero();
        assertThat(count("""
                select count(*) from (select financial_account_id,
                        sum(case when direction = 'IN' then amount else -amount end) as balance
                    from financial_transactions group by financial_account_id) x where balance < 0""")).isZero();
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private static String id(String json) {
        int i = json.indexOf("\"id\":\"") + 6;
        return json.substring(i, json.indexOf('"', i));
    }
}
