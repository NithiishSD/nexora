package com.nexora.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeightTest {

    @Test
    @DisplayName("FR-INV-15 of when one decimal has scale three")
    void of_whenOneDecimal_hasScaleThree() {
        // 42.5 kg must become 42.500 to match NUMERIC(12,3)
        assertThat(Weight.of("42.5").scale()).isEqualTo(3);
    }

    @Test
    @DisplayName("FR-INV-15 round when exactly half rounds up")
    void round_whenExactlyHalf_roundsUp() {
        assertThat(Weight.round(new BigDecimal("1.2345"))).isEqualByComparingTo("1.235");
    }

    @Test
    @DisplayName("FR-INV-15 is positive zero returns false")
    void isPositive_zero_returnsFalse() {
        assertThat(Weight.isPositive(Weight.zero())).isFalse();
    }
}
