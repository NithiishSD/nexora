package com.nexora.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.support.AbstractApiTest;

// NFR-DATA-02: stock, balances, outstanding and payables are computed; inventory_balances is the only stored projection.
class DerivedValuesTest extends AbstractApiTest {

    @Test
    @DisplayName("NFR-DATA-02 no derived columns in schema")
    void noDerivedColumnsInSchema() {
        // AC1: numeric columns of real tables (views are computed) whose table or column name says balance,
        // outstanding or payable
        var found = jdbc.queryForList("""
                select c.table_name || '.' || c.column_name
                from information_schema.columns c
                join information_schema.tables t on t.table_schema = c.table_schema and t.table_name = c.table_name
                where c.table_schema = 'public' and t.table_type = 'BASE TABLE' and c.data_type = 'numeric'
                  and (c.table_name || '.' || c.column_name) ~* '(balance|outstanding|payable)'
                order by 1""", String.class);
        assertThat(found).containsExactly("inventory_balances.quantity_kg");
        // and nothing stores a payment status, an advance or a stock figure under another name
        assertThat(jdbc.queryForList("""
                select table_name || '.' || column_name from information_schema.columns
                where table_schema = 'public' and column_name ~* '(payment_status|advance|stock|allocated_total)'""",
                String.class)).isEmpty();
    }
}
