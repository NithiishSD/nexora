package com.nexora.procurement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.nexora.inventory.domain.ProductType;
import com.nexora.parties.application.PartyService;
import com.nexora.procurement.api.dto.CreatePurchaseRequest;
import com.nexora.procurement.api.dto.PurchaseResponse;
import com.nexora.procurement.domain.PaymentType;
import com.nexora.shared.error.BusinessException;
import com.nexora.support.AbstractIntegrationTest;
import com.nexora.support.ConcurrentRunner;
import com.nexora.support.ConcurrentRunner.Outcome;
import com.nexora.support.DatabaseCleaner;
import com.nexora.support.TestDataFactory;

// FR-PROC-02: purchase numbers are unique and gap-free per year, also under concurrency and after a rollback.
// The guarantee comes from the row-locked counter in BusinessNumberGenerator (its own mutation check is
// recorded in 08-TESTING section 9); this test proves the purchase use case keeps it.
class PurchaseNumberConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    PurchaseService purchaseService;

    @Autowired
    PartyService partyService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @BeforeEach
    void clean() {
        DatabaseCleaner.clean(jdbc);
    }

    @Test
    @DisplayName("FR-PROC-02 NFR-DATA-06 NFR-TEST-05 parallel creates produce unique gap free numbers")
    void parallelCreates_produceUniqueGapFreeNumbers() throws Exception {
        UUID supplier = new TestDataFactory(partyService).supplier().id();
        String prefix = "PUR-" + Year.now(clock).getValue() + "-";

        // AC1: 10 purchases created at the same moment get 10 distinct consecutive numbers
        List<Callable<PurchaseResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> purchaseService.create(request(supplier, ProductType.RAW_SILK)));
        }
        List<Outcome<PurchaseResponse>> outcomes = ConcurrentRunner.runTogether(tasks);
        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        assertThat(outcomes.stream().map(o -> o.value().purchaseNumber()).sorted().toList())
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 10).mapToObj(n -> prefix + "%04d".formatted(n)).toList());

        // AC2: a creation that fails after taking a number rolls the counter back: the next one reuses it
        assertThatThrownBy(() -> purchaseService.create(request(supplier, ProductType.SILK_WARP)))
                .isInstanceOf(BusinessException.class);
        assertThat(purchaseService.create(request(supplier, ProductType.VUDA_WARP)).purchaseNumber())
                .isEqualTo(prefix + "0011");

        // AC3: the database itself refuses a duplicate number
        assertThatThrownBy(() -> jdbc.update("""
                insert into purchases (purchase_number, supplier_id, purchase_date, ordered_weight_kg, rate_per_kg,
                                       payment_type, credit_days, due_date)
                values (?, ?, date '2026-10-01', 1, 1, 'CASH', 0, date '2026-10-01')""", prefix + "0001", supplier))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static CreatePurchaseRequest request(UUID supplier, ProductType product) {
        return new CreatePurchaseRequest(supplier, product, LocalDate.of(2026, 10, 1), new BigDecimal("10.000"),
                new BigDecimal("800.00"), PaymentType.CREDIT, null, null, null);
    }
}
