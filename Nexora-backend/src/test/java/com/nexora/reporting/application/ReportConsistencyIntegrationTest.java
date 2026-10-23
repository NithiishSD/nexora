package com.nexora.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import com.nexora.support.AbstractApiTest;

// FR-RPT-15: one business period touching every module, then every report total is compared with the detail
// endpoints that own the figure. A mismatch fails the build (AC2).
class ReportConsistencyIntegrationTest extends AbstractApiTest {

    @Test
    @DisplayName("FR-RPT-15 report totals equal detail endpoint totals")
    void reportTotals_equalDetailEndpointTotals() throws Exception {
        String cash = createAccount("Cash", "CASH", "50000.00");
        String bank = createAccount("SBI Current", "BANK", "200000.00");

        // procurement: two suppliers, a return, a part payment
        String s1 = data.supplier().id().toString();
        String s2 = data.supplier().id().toString();
        String p1 = purchase(s1, "RAW_SILK", "100.000", "800.00");
        receipt(p1, "100.000", "98.000", "2.000");
        post("/api/v1/purchases/" + p1 + "/returns", """
                {"returnDate":"%s","returnedWeightKg":3.000,"reason":"knots"}""".formatted(today()));
        receipt(purchase(s1, "VUDA_WARP", "20.000", "500.00"), "20.000", "20.000", "0.000");
        receipt(purchase(s2, "RAW_SILK", "50.000", "700.00"), "50.000", "50.000", "0.000");
        pay("SUPPLIER", s1, "30000.00", "BANK_TRANSFER", bank);

        // sales: two delivered orders, one paid partly, one overpaid (advance); one order only placed
        String c1 = data.customer().id().toString();
        String c2 = data.customer().id().toString();
        String c3 = data.customer().id().toString();
        deliveredOrder(c1, "RAW_SILK", "40.000", "1000.00");
        deliveredOrder(c2, "VUDA_WARP", "10.000", "700.00");
        order(c3, "RAW_SILK", "5.000", "1000.00");
        pay("CUSTOMER", c1, "25000.00", "UPI", bank);
        pay("CUSTOMER", c2, "10000.00", "CASH", cash);

        // production with work, outsourcing with a payment, expenses
        String batch = id(post("/api/v1/production-batches", """
                {"productType":"KORA_WARP","productionDate":"%s","inputWeightKg":30.000}""".formatted(today())));
        post("/api/v1/production-batches/" + batch + "/start", "{}");
        String worker = data.worker().id().toString();
        post("/api/v1/work-records", """
                {"workerId":"%s","workDate":"%s","workType":"WARPING","quantityKg":30.000,"rate":5.00,
                 "productionBatchId":"%s"}""".formatted(worker, today(), batch));
        post("/api/v1/work-records", """
                {"workerId":"%s","workDate":"%s","workType":"ROLLING","hours":2.00,"rate":100.00}"""
                .formatted(worker, today()));
        post("/api/v1/production-batches/" + batch + "/complete", """
                {"outputWeightKg":28.000,"wastageWeightKg":1.000,"discrepancyWeightKg":1.000}""");
        pay("WORKER", worker, "100.00", "CASH", cash);
        String manufacturer = data.manufacturer().id().toString();
        String job = id(post("/api/v1/outsourcing-jobs", """
                {"manufacturerId":"%s","productType":"SILK_WARP","issuedWeightKg":20.000,"ratePerKg":50.00}"""
                .formatted(manufacturer)));
        post("/api/v1/outsourcing-jobs/" + job + "/issue", "{}");
        post("/api/v1/outsourcing-jobs/" + job + "/receive", """
                {"receiptDate":"%s","receivedWeightKg":15.000,"wastageWeightKg":1.000}""".formatted(today()));
        pay("MANUFACTURER", manufacturer, "300.00", "CASH", cash);
        expense("TRANSPORT", "BUSINESS", "1000.00", cash);
        expense("OTHER", "PERSONAL", "500.00", cash);

        String period = "from=" + today().minusDays(30) + "&to=" + today();

        // purchases: report totals = the purchase list (AC1)
        JsonNode purchases = read("/api/v1/reports/purchases?" + period).get("totals");
        JsonNode purchaseList = read("/api/v1/purchases?size=100&" + period).get("content");
        assertEqual(purchases.get("orderedKg"), sum(purchaseList, "orderedWeightKg"));
        assertEqual(purchases.get("orderedValue"), sum(purchaseList, "orderedValue"));
        assertEqual(purchases.get("receivedKg"), sum(purchaseList, "receivedWeightKg"));

        // customers: outstanding and advance = the per-customer endpoints
        JsonNode customers = read("/api/v1/reports/customer-outstanding").get("totals");
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal advance = BigDecimal.ZERO;
        for (String c : List.of(c1, c2, c3)) {
            JsonNode o = read("/api/v1/customers/" + c + "/outstanding");
            outstanding = outstanding.add(o.get("totalOutstanding").decimalValue());
            advance = advance.add(o.get("advance").decimalValue());
        }
        assertEqual(customers.get("outstanding"), outstanding);
        assertEqual(customers.get("advance"), advance);

        // suppliers: ageing total = the payables overview
        JsonNode ageing = read("/api/v1/reports/supplier-payables");
        assertEqual(ageing.get("total"), read("/api/v1/payables/suppliers").get("totalPayable").decimalValue());

        // sales: revenue = the delivered orders' totals
        JsonNode sales = read("/api/v1/reports/sales?" + period).get("totals");
        assertEqual(sales.get("revenueDelivered"),
                sum(read("/api/v1/orders?status=DELIVERED&size=100").get("content"), "totalAmount"));
        assertEqual(sales.get("orderValuePlaced"), sum(read("/api/v1/orders?size=100").get("content"), "totalAmount"));

        // expenses: = the expense summary
        JsonNode expenses = read("/api/v1/reports/expenses?" + period);
        JsonNode summary = read("/api/v1/expenses/summary?" + period);
        assertEqual(expenses.get("businessTotal"), summary.get("businessTotal").decimalValue());
        assertEqual(expenses.get("personalDrawingsTotal"), summary.get("personalDrawingsTotal").decimalValue());

        // stock: closing per location = the stock summary balances
        JsonNode stock = read("/api/v1/inventory/summary");
        for (String product : List.of("RAW_SILK", "VUDA_WARP", "KORA_WARP", "SILK_WARP")) {
            for (JsonNode line : read("/api/v1/reports/inventory-movements?productType=" + product + "&" + period)) {
                assertEqual(line.get("closingKg"), balance(stock, product, line.get("location").asString()));
            }
        }

        // money: closing per account = the account balance; cash + banks = the dashboard's available money
        BigDecimal closingTotal = BigDecimal.ZERO;
        for (JsonNode account : read("/api/v1/reports/cash-bank?" + period)) {
            assertEqual(account.get("closingBalance"), read("/api/v1/finance/accounts/"
                    + account.get("account").get("id").asString()).get("balance").decimalValue());
            closingTotal = closingTotal.add(account.get("closingBalance").decimalValue());
        }

        // work: = the worker's payable for the same range
        JsonNode workers = read("/api/v1/reports/worker-payable?" + period).get(0);
        JsonNode workerPayable = read("/api/v1/workers/" + worker + "/payable?" + period);
        assertEqual(workers.get("earned"), workerPayable.get("workValue").decimalValue());
        assertEqual(workers.get("paid"), workerPayable.get("paid").decimalValue());
        assertEqual(workers.get("pending"), workerPayable.get("pending").decimalValue());

        // outsourcing: pending = the manufacturer's payable; production: = the completed batch
        JsonNode outsourcing = read("/api/v1/reports/outsourcing?" + period).get("totals");
        assertEqual(outsourcing.get("pending"),
                read("/api/v1/manufacturers/" + manufacturer + "/payable").get("totalPayable").decimalValue());
        JsonNode production = read("/api/v1/reports/production?" + period).get("totals");
        assertEqual(production.get("inputKg"), read("/api/v1/production-batches/" + batch).get("inputWeightKg")
                .decimalValue());

        // the dashboard agrees with the reports
        JsonNode dashboard = read("/api/v1/dashboard/summary");
        assertEqual(dashboard.get("totalAvailable"), closingTotal);
        assertEqual(dashboard.get("customerReceivables"), customers.get("outstanding").decimalValue());
        assertEqual(dashboard.get("customerAdvances"), customers.get("advance").decimalValue());
        assertEqual(dashboard.get("supplierPayables"), ageing.get("total").decimalValue());
        assertEqual(dashboard.get("workerPayable"), workers.get("pending").decimalValue());
        assertEqual(dashboard.get("manufacturerPayable"), outsourcing.get("pending").decimalValue());
        assertEqual(dashboard.get("stock").get("rawSilkKg"), balance(stock, "RAW_SILK", "RAW_STOCK"));
        // and the figures are the expected ones, so the comparisons above compared something real
        assertEqual(dashboard.get("stock").get("rawSilkKg"), new BigDecimal("55.000"));   // 145 - 40 - 30 - 20
        assertEqual(dashboard.get("customerReceivables"), new BigDecimal("15000.00"));     // 40000 - 25000
        assertEqual(dashboard.get("customerAdvances"), new BigDecimal("3000.00"));         // 10000 - 7000
    }

    private static void assertEqual(JsonNode actual, BigDecimal expected) {
        assertThat(actual.decimalValue()).isEqualByComparingTo(expected);
    }

    private static BigDecimal sum(JsonNode rows, String field) {
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode row : rows) {
            total = total.add(row.get(field).decimalValue());
        }
        return total;
    }

    private static BigDecimal balance(JsonNode summary, String product, String location) {
        for (JsonNode b : summary.get("balances")) {
            if (b.get("productType").asString().equals(product) && b.get("location").asString().equals(location)) {
                return b.get("quantityKg").decimalValue();
            }
        }
        throw new AssertionError("no balance " + product + " " + location);
    }

    private JsonNode read(String path) throws Exception {
        return body(asOwner(get(path)).andExpect(status().isOk()));
    }

    private JsonNode post(String path, String json) throws Exception {
        return body(asOwner(postWithKey(path, json)).andExpect(status().is2xxSuccessful()));
    }

    private static String id(JsonNode node) {
        return node.get("id").asString();
    }

    private String purchase(String supplier, String product, String kg, String rate) throws Exception {
        return id(post("/api/v1/purchases", """
                {"supplierId":"%s","productType":"%s","purchaseDate":"%s","orderedWeightKg":%s,"ratePerKg":%s,
                 "paymentType":"CREDIT"}""".formatted(supplier, product, today(), kg, rate)));
    }

    private void receipt(String purchase, String received, String accepted, String rejected) throws Exception {
        post("/api/v1/purchases/" + purchase + "/receipts", """
                {"receiptDate":"%s","receivedWeightKg":%s,"acceptedWeightKg":%s,"rejectedWeightKg":%s}"""
                .formatted(today(), received, accepted, rejected));
    }

    private String order(String customer, String product, String kg, String rate) throws Exception {
        return id(post("/api/v1/orders", """
                {"customerId":"%s","orderDate":"%s","items":[{"productType":"%s","requiredWeightKg":%s,"ratePerKg":%s}]}"""
                .formatted(customer, today(), product, kg, rate)));
    }

    private void deliveredOrder(String customer, String product, String kg, String rate) throws Exception {
        String id = order(customer, product, kg, rate);
        post("/api/v1/orders/" + id + "/confirm", "");
        post("/api/v1/orders/" + id + "/ready", "");
        post("/api/v1/orders/" + id + "/deliver", "{\"deliveryDate\":\"%s\"}".formatted(today()));
    }

    private void pay(String purpose, String party, String amount, String method, String account) throws Exception {
        post("/api/v1/payments", """
                {"purpose":"%s","partyId":"%s","amount":%s,"paymentMethod":"%s","financialAccountId":"%s"}"""
                .formatted(purpose, party, amount, method, account));
    }

    private void expense(String category, String kind, String amount, String account) throws Exception {
        post("/api/v1/expenses", """
                {"category":"%s","expenseKind":"%s","amount":%s,"expenseDate":"%s","financialAccountId":"%s",
                 "paymentMethod":"CASH"}""".formatted(category, kind, amount, today(), account));
    }
}
