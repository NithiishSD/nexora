package com.nexora.workforce.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.shared.error.FieldValidationException;

class WorkRecordTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);

    @Test
    @DisplayName("FR-WORK-02 rolling requires hours only")
    void rolling_requiresHoursOnly() {
        // FR-WORK-01 AC1: 8.00 h x 100.00 = 800.00
        WorkRecord w = rolling(new BigDecimal("8.00"), null, "100.00");
        assertThat(w.getAmount()).isEqualByComparingTo("800.00");
        assertThat(w.getQuantityKg()).isNull();
        // HALF_UP: 7.25 h x 33.33 = 241.6425 -> 241.64; 0.15 h x 0.10 = 0.015 -> 0.02
        assertThat(rolling(new BigDecimal("7.25"), null, "33.33").getAmount()).isEqualByComparingTo("241.64");
        assertThat(rolling(new BigDecimal("0.15"), null, "0.10").getAmount()).isEqualByComparingTo("0.02");

        // AC3: no hours; FR-WORK-02 AC1: hours and a quantity
        assertThatThrownBy(() -> rolling(null, null, "100.00"))
                .isInstanceOfSatisfying(FieldValidationException.class, e -> assertThat(e.getField()).isEqualTo("hours"));
        assertThatThrownBy(() -> rolling(new BigDecimal("8.00"), new BigDecimal("1.000"), "100.00"))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(e.getField()).isEqualTo("quantityKg"));
    }

    @Test
    @DisplayName("FR-WORK-02 warping requires quantity only")
    void warping_requiresQuantityOnly() {
        // FR-WORK-01 AC2: 25.500 kg x 40.00 = 1020.00
        WorkRecord w = warping(null, new BigDecimal("25.500"), "40.00");
        assertThat(w.getAmount()).isEqualByComparingTo("1020.00");
        assertThat(w.getHours()).isNull();

        // AC4: no quantity; FR-WORK-02 AC2: a quantity and hours
        assertThatThrownBy(() -> warping(null, null, "40.00"))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(e.getField()).isEqualTo("quantityKg"));
        assertThatThrownBy(() -> warping(new BigDecimal("1.00"), new BigDecimal("25.500"), "40.00"))
                .isInstanceOfSatisfying(FieldValidationException.class, e -> assertThat(e.getField()).isEqualTo("hours"));
    }

    private static WorkRecord rolling(BigDecimal hours, BigDecimal kg, String rate) {
        return WorkRecord.record(UUID.randomUUID(), DAY, WorkType.ROLLING, hours, kg, new BigDecimal(rate), null, null);
    }

    private static WorkRecord warping(BigDecimal hours, BigDecimal kg, String rate) {
        return WorkRecord.record(UUID.randomUUID(), DAY, WorkType.WARPING, hours, kg, new BigDecimal(rate), null, null);
    }
}
