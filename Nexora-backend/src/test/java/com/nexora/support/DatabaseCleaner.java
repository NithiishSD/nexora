package com.nexora.support;

import org.springframework.jdbc.core.JdbcTemplate;

// Empties the business tables between API tests, in one statement (08-TESTING section 6).
// TRUNCATE ... CASCADE also clears the rows that reference these tables, and (unlike DELETE) does not fire
// row triggers, so it works for append-only ledger tables too. Identity data (users, roles, permissions)
// and flyway_schema_history are never touched: the bootstrap owner lives for the whole test run.
public final class DatabaseCleaner {

    // Add each new business table here when its module arrives.
    private static final String TABLES = String.join(", ",
            "parties", "party_roles", "party_phone_numbers",
            "inventory_balances", "material_movements",
            "purchases", "material_receipts", "supplier_returns",
            "customer_orders", "order_items",
            "financial_accounts", "financial_transactions", "payments", "expenses", "loans", "chits",
            "account_transfers", "opening_obligations", "customer_payment_allocations",
            "supplier_payment_allocations", "idempotency_keys", "audit_log",
            "production_batches", "production_order_items",
            "outsourcing_jobs", "outsourcing_order_items", "manufacturer_payment_allocations",
            "work_records", "worker_payment_allocations", "notifications", "documents",
            "business_number_sequences", "refresh_tokens");

    private DatabaseCleaner() {
    }

    public static void clean(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE " + TABLES + " CASCADE");
        // configuration rows are seeded by the migration: put them back to the seed instead of deleting them
        jdbc.update("UPDATE notification_configurations SET enabled = TRUE, days_before_due = 3,"
                + " overdue_repeat_days = 1, priority = 'HIGH'");
        jdbc.update("DELETE FROM expense_categories WHERE name NOT IN "
                + "('TRANSPORT', 'ELECTRICITY', 'RENT', 'WORKER', 'MAINTENANCE', 'PACKING', 'OTHER')");
        jdbc.update("UPDATE expense_categories SET active = TRUE");
    }
}
