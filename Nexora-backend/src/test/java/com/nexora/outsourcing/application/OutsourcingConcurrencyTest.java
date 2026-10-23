package com.nexora.outsourcing.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexora.inventory.application.InventoryService;
import com.nexora.inventory.domain.ProductType;
import com.nexora.outsourcing.api.dto.CreateJobRequest;
import com.nexora.outsourcing.api.dto.JobResponse;
import com.nexora.parties.application.PartyService;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.support.AbstractIntegrationTest;
import com.nexora.support.ConcurrentRunner;
import com.nexora.support.ConcurrentRunner.Outcome;
import com.nexora.support.DatabaseCleaner;
import com.nexora.support.TestDataFactory;

// FR-OUT-10: two jobs issued at the same moment compete for one raw-silk balance. Mutation check (by hand, never
// committed): without @Lock(PESSIMISTIC_WRITE) on InventoryBalanceRepository.findForUpdate the loser fails with an
// optimistic-lock error instead of INSUFFICIENT_INVENTORY.
class OutsourcingConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    OutsourcingService outsourcing;

    @Autowired
    InventoryService inventory;

    @Autowired
    PartyService partyService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        DatabaseCleaner.clean(jdbc);
    }

    @Test
    @DisplayName("FR-OUT-10 NFR-TEST-05 parallel issues cannot oversubscribe raw silk")
    void parallelIssues_cannotOversubscribeRawSilk() throws Exception {
        TestDataFactory data = new TestDataFactory(partyService, inventory, new TransactionTemplate(transactionManager));
        data.rawSilkInStock("110.000");
        UUID manufacturer = data.manufacturer().id();
        // an earlier job creates the EXTERNAL_WIP balance row; otherwise the second issue waits on that row's insert
        // (unique index) and the race is hidden even without the balance lock
        outsourcing.issue(create(manufacturer, "10.000"), null);
        UUID first = create(manufacturer, "80.000");
        UUID second = create(manufacturer, "70.000");

        List<Outcome<JobResponse>> outcomes = ConcurrentRunner.runTogether(List.of(
                () -> outsourcing.issue(first, null), () -> outsourcing.issue(second, null)));

        // exactly one issues; the other sees the reduced stock and stays CREATED
        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes.stream().filter(o -> !o.succeeded()).findFirst().orElseThrow().error())
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_INVENTORY));
        BigDecimal left = jdbc.queryForObject("select quantity_kg from inventory_balances where product_type ="
                + " 'RAW_SILK' and location = 'RAW_STOCK'", BigDecimal.class);
        assertThat(left.compareTo(new BigDecimal("20.000")) == 0 || left.compareTo(new BigDecimal("30.000")) == 0)
                .as("raw silk is 20 or 30, never negative, got %s", left).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from outsourcing_jobs where status = 'CREATED'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from material_movements where movement_type = 'OUTSOURCE_ISSUE'",
                Integer.class)).isEqualTo(2);
    }

    private UUID create(UUID manufacturer, String kg) {
        return outsourcing.create(new CreateJobRequest(manufacturer, ProductType.KORA_WARP, new BigDecimal(kg),
                new BigDecimal("120.00"), null, null)).id();
    }
}
