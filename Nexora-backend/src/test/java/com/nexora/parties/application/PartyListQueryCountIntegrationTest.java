package com.nexora.parties.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.nexora.support.AbstractIntegrationTest;
import com.nexora.support.TestDataFactory;

import jakarta.persistence.EntityManagerFactory;

// Counts the SQL statements Hibernate prepares for one list page. With N+1 loading, a page of 100 would
// need about 100 more statements than a page of 20; with batch loading both need the same few.
class PartyListQueryCountIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    PartyService partyService;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("NFR-PERF-07 list statement count does not grow with page size")
    void list_statementCount_doesNotGrowWithPageSize() {
        TestDataFactory data = new TestDataFactory(partyService);
        for (int i = 0; i < 100; i++) {
            data.customer();                         // every party has a role and a phone to load
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);       // switched on for this test only, at runtime
        try {
            long page20 = statementsFor(statistics, 20);
            long page100 = statementsFor(statistics, 100);

            assertThat(page100).isEqualTo(page20);
            assertThat(page100).isLessThanOrEqualTo(6);   // NFR-PERF-07 AC1: at most 6
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    private long statementsFor(Statistics statistics, int pageSize) {
        statistics.clear();
        var page = partyService.list(null, null, null,
                PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        assertThat(page.content()).hasSize(pageSize);
        assertThat(page.content()).allSatisfy(p -> assertThat(p.roles()).isNotEmpty());
        return statistics.getPrepareStatementCount();
    }
}
