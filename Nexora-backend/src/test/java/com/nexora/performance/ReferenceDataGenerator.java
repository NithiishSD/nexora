package com.nexora.performance;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexora.finance.application.FinanceService;
import com.nexora.finance.application.TransactionCommand;
import com.nexora.finance.domain.Direction;
import com.nexora.finance.domain.TransactionReferenceType;
import com.nexora.finance.domain.TransactionType;
import com.nexora.inventory.application.InventoryService;
import com.nexora.inventory.application.MovementCommand;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;

// NFR-PERF-01: the reference workload of five years, built through the front door so every ledger rule holds.
// - Business layer, through the HTTP API: each month 25 purchases with receipts, 25 orders confirmed, readied and
//   delivered, 25 customer payments, 15 supplier payments, 10 expenses (real records for lists and reports).
// - Volume layer, through InventoryService and FinanceService in one transaction per day: 54 stock movements
//   (vuda warp in and out by ADJUSTMENT) and 54 ledger rows (ADJUSTMENT in and out over four accounts), with the
//   services' locks, numbering and no-negative checks. Together: over 100000 movements and 100000 transactions.
final class ReferenceDataGenerator {

    static final int YEARS = 5;
    private static final int PER_MONTH = 25;
    private static final int MOVEMENTS_PER_DAY = 54;
    private static final int LEDGER_IN_PER_DAY = 36;
    private static final int LEDGER_OUT_PER_DAY = 18;
    private static final String REASON = "Reference workload";

    record Result(Duration took, String cash, List<String> banks, List<String> suppliers, List<String> customers) {
    }

    private final PerfClient api;
    private final InventoryService inventory;
    private final FinanceService finance;
    private final TransactionTemplate tx;
    private final LocalDate today;

    ReferenceDataGenerator(PerfClient api, InventoryService inventory, FinanceService finance,
                           PlatformTransactionManager transactions, LocalDate today) {
        this.api = api;
        this.inventory = inventory;
        this.finance = finance;
        this.tx = new TransactionTemplate(transactions);
        this.today = today;
    }

    Result generate() {
        long start = System.nanoTime();
        LocalDate first = today.minusYears(YEARS).plusDays(1);
        String cash = account("Cash", "CASH", "1000000.00", first);
        List<String> banks = List.of(account("SBI Current", "BANK", "5000000.00", first),
                account("Canara Savings", "BANK", "5000000.00", first),
                account("IOB Current", "BANK", "5000000.00", first));
        List<String> suppliers = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            suppliers.add(party("Supplier " + i, "SUPPLIER", ",\"defaultCreditDays\":15"));
        }
        List<String> customers = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            customers.add(party("Customer " + i, "CUSTOMER", ""));
        }

        for (int month = 0; month < YEARS * 12; month++) {
            businessMonth(month, first.plusMonths(month), cash, banks, suppliers, customers);
        }
        List<String> accounts = new ArrayList<>(banks);
        accounts.add(cash);
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            volumeDay(day, accounts);
        }
        return new Result(Duration.ofNanos(System.nanoTime() - start), cash, banks, suppliers, customers);
    }

    private void businessMonth(int month, LocalDate start, String cash, List<String> banks, List<String> suppliers,
                               List<String> customers) {
        for (int i = 0; i < PER_MONTH; i++) {
            LocalDate day = min(start.plusDays(i));
            String purchase = id(api.post("/api/v1/purchases", """
                    {"supplierId":"%s","productType":"RAW_SILK","purchaseDate":"%s","orderedWeightKg":100.000,
                     "ratePerKg":800.00,"paymentType":"CREDIT","creditDays":15}"""
                    .formatted(suppliers.get((month * PER_MONTH + i) % suppliers.size()), day)));
            api.post("/api/v1/purchases/" + purchase + "/receipts", """
                    {"receiptDate":"%s","receivedWeightKg":100.000,"acceptedWeightKg":98.000,"rejectedWeightKg":2.000}"""
                    .formatted(day));
        }
        for (int i = 0; i < PER_MONTH; i++) {
            LocalDate day = min(start.plusDays(i));
            String customer = customers.get((month * PER_MONTH + i) % customers.size());
            String order = id(api.post("/api/v1/orders", """
                    {"customerId":"%s","orderDate":"%s",
                     "items":[{"productType":"RAW_SILK","requiredWeightKg":80.000,"ratePerKg":1000.00}]}"""
                    .formatted(customer, day)));
            api.post("/api/v1/orders/" + order + "/confirm", "");
            api.post("/api/v1/orders/" + order + "/ready", "");
            api.post("/api/v1/orders/" + order + "/deliver", "{\"deliveryDate\":\"" + day + "\"}");
            api.post("/api/v1/payments", """
                    {"purpose":"CUSTOMER","partyId":"%s","amount":60000.00,"paymentMethod":"UPI",
                     "financialAccountId":"%s","paymentDate":"%s"}""".formatted(customer, banks.get(i % 3), day));
        }
        for (int i = 0; i < 15; i++) {
            api.post("/api/v1/payments", """
                    {"purpose":"SUPPLIER","partyId":"%s","amount":50000.00,"paymentMethod":"BANK_TRANSFER",
                     "financialAccountId":"%s","paymentDate":"%s"}"""
                    .formatted(suppliers.get(i), banks.get(i % 3), min(start.plusDays(26))));
        }
        for (int i = 0; i < 10; i++) {
            api.post("/api/v1/expenses", """
                    {"category":"TRANSPORT","expenseKind":"BUSINESS","amount":500.00,"expenseDate":"%s",
                     "financialAccountId":"%s","paymentMethod":"CASH"}""".formatted(min(start.plusDays(i)), cash));
        }
    }

    // One day of volume in one transaction: stock in before stock out, money in before money out.
    private void volumeDay(LocalDate day, List<String> accounts) {
        tx.executeWithoutResult(status -> {
            List<MovementCommand> moves = new ArrayList<>();
            BigDecimal kg = new BigDecimal("10.000");
            for (int i = 0; i < MOVEMENTS_PER_DAY / 2; i++) {
                moves.add(new MovementCommand(ProductType.VUDA_WARP, null, MovementType.ADJUSTMENT,
                        StockLocation.SUPPLIER, StockLocation.RAW_STOCK, kg, day, null, null, null, REASON, null));
            }
            for (int i = 0; i < MOVEMENTS_PER_DAY / 2; i++) {
                moves.add(new MovementCommand(ProductType.VUDA_WARP, null, MovementType.ADJUSTMENT,
                        StockLocation.RAW_STOCK, StockLocation.CUSTOMER, kg, day, null, null, null, REASON, null));
            }
            inventory.recordMovements(moves);
            for (int i = 0; i < LEDGER_IN_PER_DAY; i++) {
                ledger(accounts.get(i % accounts.size()), Direction.IN, "1000.00", day);
            }
            for (int i = 0; i < LEDGER_OUT_PER_DAY; i++) {
                ledger(accounts.get(i % accounts.size()), Direction.OUT, "1500.00", day);
            }
        });
    }

    private void ledger(String account, Direction direction, String amount, LocalDate day) {
        finance.recordTransaction(new TransactionCommand(UUID.fromString(account), direction, new BigDecimal(amount),
                TransactionType.ADJUSTMENT, day, REASON, null, TransactionReferenceType.NONE, null));
    }

    private String account(String name, String type, String opening, LocalDate date) {
        String bank = type.equals("BANK") ? ",\"bankName\":\"" + name + " bank\"" : "";
        return id(api.post("/api/v1/finance/accounts", """
                {"accountName":"%s","accountType":"%s"%s,"openingBalance":%s,"openingDate":"%s"}"""
                .formatted(name, type, bank, opening, date)));
    }

    private String party(String name, String role, String extra) {
        return id(api.post("/api/v1/parties", "{\"name\":\"" + name + "\",\"roles\":[\"" + role + "\"]" + extra + "}"));
    }

    private LocalDate min(LocalDate day) {
        return day.isAfter(today) ? today : day;
    }

    private static String id(tools.jackson.databind.JsonNode node) {
        return node.get("id").asString();
    }
}
