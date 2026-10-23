package com.nexora.production.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.inventory.domain.ProductType;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;

class ProductionBatchTest {

    private static final Instant NOW = Instant.parse("2026-10-08T05:00:00Z");

    @Test
    @DisplayName("FR-PROD-03 FR-PROD-04 complete input not reconciled throws production not reconciled")
    void complete_inputNotReconciled_throwsProductionNotReconciled() {
        ProductionBatch batch = started("100.000");

        // 94 + 10 + 0 = 104 and 94 + 0 + 0 = 94: both refused, the batch stays IN_PROGRESS
        assertThatThrownBy(() -> batch.complete(kg("94.000"), kg("10.000"), kg("0.000"), null, NOW))
                .isInstanceOf(ProductionNotReconciledException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ErrorCode.PRODUCTION_NOT_RECONCILED));
        assertThatThrownBy(() -> batch.complete(kg("94.000"), kg("2.000"), kg("0.000"), null, NOW))
                .isInstanceOf(ProductionNotReconciledException.class);
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.IN_PROGRESS);

        // 94 + 4 + 2 = 100: completed, the discrepancy is kept visible and wastage is 4.00 %
        batch.complete(kg("94.000"), kg("4.000"), kg("2.000"), null, NOW);
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(batch.getDiscrepancyWeightKg()).isEqualByComparingTo("2.000");
        assertThat(batch.wastagePercent()).isEqualByComparingTo("4.00");
    }

    @Test
    @DisplayName("FR-PROD-05 NFR-TEST-08 cancel after start throws invalid state transition")
    void cancel_afterStart_throwsInvalidStateTransition() {
        ProductionBatch planned = planned("50.000");
        planned.cancel();
        assertThat(planned.getStatus()).isEqualTo(BatchStatus.CANCELLED);

        ProductionBatch started = started("50.000");
        assertThatThrownBy(started::cancel).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        // the other illegal moves of the state machine
        assertThatThrownBy(() -> started.start(NOW)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        assertThatThrownBy(() -> planned("1.000").complete(kg("1.000"), kg("0"), kg("0"), null, NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        assertThatThrownBy(() -> planned.start(NOW)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        // vuda warp and raw silk are never produced
        assertThatThrownBy(() -> ProductionBatch.plan("BAT-2026-0009", ProductType.VUDA_WARP, LocalDate.of(2026, 10, 8),
                kg("1.000"), null, List.of())).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_PRODUCT_FOR_OPERATION));
    }

    @Test
    @DisplayName("FR-PROD-10 completed cannot be edited")
    void completed_cannotBeEdited() {
        ProductionBatch batch = started("10.000");
        assertThatThrownBy(() -> batch.edit(LocalDate.of(2026, 10, 9), kg("11.000"), null, List.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        batch.complete(kg("10.000"), kg("0.000"), kg("0.000"), null, NOW);
        assertThatThrownBy(() -> batch.edit(LocalDate.of(2026, 10, 9), kg("11.000"), null, List.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        assertThat(batch.getInputWeightKg()).isEqualByComparingTo("10.000");
        // while planned, it can be changed
        ProductionBatch planned = planned("100.000");
        planned.edit(LocalDate.of(2026, 10, 9), kg("110.000"), "more silk", List.of());
        assertThat(planned.getInputWeightKg()).isEqualByComparingTo("110.000");
    }

    private static ProductionBatch planned(String input) {
        return ProductionBatch.plan("BAT-2026-0001", ProductType.KORA_WARP, LocalDate.of(2026, 10, 8), kg(input), null,
                List.of());
    }

    private static ProductionBatch started(String input) {
        ProductionBatch b = planned(input);
        b.start(NOW);
        return b;
    }

    private static BigDecimal kg(String value) {
        return new BigDecimal(value);
    }
}
