package com.nexora.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.nexora.support.AbstractApiTest;

// NFR-DATA-05: every relationship is a foreign key, every foreign key is backed by an index starting with its
// columns, and constraints follow the naming convention.
class ForeignKeyIndexIntegrationTest extends AbstractApiTest {

    @Test
    @DisplayName("NFR-DATA-05 every foreign key column has index")
    void everyForeignKeyColumn_hasIndex() {
        // AC1
        assertThat(unindexedForeignKeys()).isEmpty();
        // the query finds what it should: a probe table with an unindexed foreign key, then indexed
        jdbc.execute("create table fk_probe (id uuid primary key, party_id uuid,"
                + " constraint fk_fk_probe_party foreign key (party_id) references parties (id))");
        try {
            assertThat(unindexedForeignKeys()).containsExactly("fk_probe.fk_fk_probe_party");
            jdbc.execute("create index ix_fk_probe_party_id on fk_probe (party_id)");
            assertThat(unindexedForeignKeys()).isEmpty();
        } finally {
            jdbc.execute("drop table fk_probe");
        }
        // named constraints: fk_, uq_, ck_ (primary keys keep PostgreSQL's <table>_pkey)
        assertThat(jdbc.queryForList("""
                select conrelid::regclass::text || '.' || conname from pg_constraint
                where connamespace = 'public'::regnamespace and contype in ('f', 'u', 'c')
                  and conname !~ '^(fk|uq|ck)_' order by 1""", String.class)).isEmpty();
        // AC2: a purchase for a supplier that does not exist is refused by the database itself
        assertThatThrownBy(() -> jdbc.update("""
                insert into purchases (purchase_number, supplier_id, purchase_date, ordered_weight_kg, rate_per_kg,
                                       payment_type, due_date)
                values ('PUR-TEST-0001', ?::uuid, current_date, 1, 1, 'CREDIT', current_date)""",
                UUID.randomUUID().toString())).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_purchases_supplier");
    }

    private List<String> unindexedForeignKeys() {
        return jdbc.queryForList("""
                select c.conrelid::regclass::text || '.' || c.conname
                from pg_constraint c
                where c.contype = 'f' and c.connamespace = 'public'::regnamespace
                  and not exists (
                      select 1 from pg_index i
                      where i.indrelid = c.conrelid
                        and array_to_string((string_to_array(i.indkey::text, ' '))[1:cardinality(c.conkey)], ',')
                            = array_to_string(c.conkey, ','))
                order by 1""", String.class);
    }
}
