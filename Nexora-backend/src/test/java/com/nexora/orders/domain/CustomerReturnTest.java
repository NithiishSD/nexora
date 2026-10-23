package com.nexora.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.inventory.domain.ProductType;

class CustomerReturnTest {

    @Test
    @DisplayName("FR-ORD-20 FR-INV-17 customer return holds rounded weight and attributes")
    void customerReturn_holdsRoundedWeightAndAttributes() {
        CustomerOrder order = CustomerOrder.place("ORD-2026-0001", UUID.randomUUID(), LocalDate.now(),
                LocalDate.now().plusDays(7), "Test note",
                List.of(new OrderLine(ProductType.SILK_WARP, new BigDecimal("10.000"), new BigDecimal("5000.00"),
                        "4800 ends", null)));
        OrderItem item = order.getItems().getFirst();

        CustomerReturn ret = CustomerReturn.of(order, item, "CRN-2026-0001", LocalDate.now(),
                new BigDecimal("2.5004"), true, "Edge defect", "Credited");

        assertThat(ret.getOrder()).isEqualTo(order);
        assertThat(ret.getOrderItem()).isEqualTo(item);
        assertThat(ret.getReturnNumber()).isEqualTo("CRN-2026-0001");
        assertThat(ret.getReturnedWeightKg()).isEqualByComparingTo("2.500");
        assertThat(ret.isUsable()).isTrue();
        assertThat(ret.getReason()).isEqualTo("Edge defect");
        assertThat(ret.getNotes()).isEqualTo("Credited");
    }

    @Test
    @DisplayName("FR-ORD-20 rejection when required fields are missing")
    void customerReturn_missingFields_throwsException() {
        CustomerOrder order = CustomerOrder.place("ORD-2026-0001", UUID.randomUUID(), LocalDate.now(),
                LocalDate.now().plusDays(7), "Test note",
                List.of(new OrderLine(ProductType.SILK_WARP, new BigDecimal("10.000"), new BigDecimal("5000.00"),
                        "4800 ends", null)));
        OrderItem item = order.getItems().getFirst();

        assertThatThrownBy(() -> CustomerReturn.of(null, item, "CRN-2026-0001", LocalDate.now(),
                new BigDecimal("1.000"), true, "Defect", null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> CustomerReturn.of(order, null, "CRN-2026-0001", LocalDate.now(),
                new BigDecimal("1.000"), true, "Defect", null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> CustomerReturn.of(order, item, null, LocalDate.now(),
                new BigDecimal("1.000"), true, "Defect", null))
                .isInstanceOf(NullPointerException.class);
    }
}
