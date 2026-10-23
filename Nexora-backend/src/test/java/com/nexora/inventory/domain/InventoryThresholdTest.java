package com.nexora.inventory.domain;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.shared.error.FieldValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryThresholdTest {

    @Test
    @DisplayName("Valid product and location threshold initializes with rounded kg")
    void validPair_initializesCorrectly() {
        InventoryThreshold t = new InventoryThreshold(ProductType.RAW_SILK, StockLocation.RAW_STOCK, new BigDecimal("50.5"));
        assertThat(t.getProductType()).isEqualTo(ProductType.RAW_SILK);
        assertThat(t.getLocation()).isEqualTo(StockLocation.RAW_STOCK);
        assertThat(t.getMinThresholdKg()).isEqualByComparingTo("50.500");
    }

    @Test
    @DisplayName("Negative threshold weight is rejected")
    void negativeWeight_throwsValidationException() {
        assertThatThrownBy(() -> new InventoryThreshold(ProductType.RAW_SILK, StockLocation.RAW_STOCK, new BigDecimal("-1.000")))
                .isInstanceOf(FieldValidationException.class)
                .hasMessageContaining("Minimum threshold must be 0 kg or greater");
    }

    @Test
    @DisplayName("Illegal product and location pair is rejected")
    void illegalPair_throwsValidationException() {
        assertThatThrownBy(() -> new InventoryThreshold(ProductType.RAW_SILK, StockLocation.FINISHED_STOCK, new BigDecimal("20.000")))
                .isInstanceOf(FieldValidationException.class)
                .hasMessageContaining("cannot hold");

        assertThatThrownBy(() -> new InventoryThreshold(ProductType.SILK_WARP, StockLocation.RAW_STOCK, new BigDecimal("20.000")))
                .isInstanceOf(FieldValidationException.class)
                .hasMessageContaining("cannot hold");

        assertThatThrownBy(() -> new InventoryThreshold(ProductType.RAW_SILK, StockLocation.CUSTOMER, new BigDecimal("20.000")))
                .isInstanceOf(FieldValidationException.class)
                .hasMessageContaining("cannot hold");
    }

    @Test
    @DisplayName("Weight with more than 3 decimal places is rejected")
    void excessiveDecimals_throwsValidationException() {
        InventoryThreshold t = new InventoryThreshold(ProductType.RAW_SILK, StockLocation.RAW_STOCK, new BigDecimal("10.000"));
        assertThatThrownBy(() -> t.update(new BigDecimal("10.1234")))
                .isInstanceOf(FieldValidationException.class)
                .hasMessageContaining("at most 3 decimals");
    }

    @Test
    @DisplayName("Updating threshold with 0 kg is allowed")
    void zeroThreshold_isAllowed() {
        InventoryThreshold t = new InventoryThreshold(ProductType.RAW_SILK, StockLocation.RAW_STOCK, new BigDecimal("10.000"));
        t.update(BigDecimal.ZERO);
        assertThat(t.getMinThresholdKg()).isEqualByComparingTo("0.000");
    }
}
