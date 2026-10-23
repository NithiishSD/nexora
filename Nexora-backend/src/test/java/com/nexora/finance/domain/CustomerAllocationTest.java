package com.nexora.finance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;

class CustomerAllocationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    @Test
    @DisplayName("FR-PAY-02 oldest first 20k30k40k pay45k settles athen b25k")
    void oldestFirst_20k30k40kPay45k_settlesAThenB25k() {
        Obligation a = item("ORD-2026-0001 line 1", "20000.00");
        Obligation b = item("ORD-2026-0002 line 1", "30000.00");
        Obligation c = item("ORD-2026-0003 line 1", "40000.00");

        List<PlannedAllocation> plan = AllocationPlanner.oldestFirst(new BigDecimal("45000.00"), List.of(a, b, c));

        // A is settled, B gets 25000 (5000 still open), C is untouched
        assertThat(plan).hasSize(2);
        assertThat(plan.get(0).targetId()).isEqualTo(a.targetId());
        assertThat(plan.get(0).amount()).isEqualByComparingTo("20000.00");
        assertThat(plan.get(1).targetId()).isEqualTo(b.targetId());
        assertThat(plan.get(1).amount()).isEqualByComparingTo("25000.00");

        // more than everything owed: all settled, the rest stays unallocated (advance)
        List<PlannedAllocation> all = AllocationPlanner.oldestFirst(new BigDecimal("100000.00"), List.of(a, b, c));
        assertThat(all).extracting(PlannedAllocation::amount).map(BigDecimal::toPlainString)
                .containsExactly("20000.00", "30000.00", "40000.00");
        // nothing owed: nothing allocated
        assertThat(AllocationPlanner.oldestFirst(new BigDecimal("5000.00"), List.of())).isEmpty();
    }

    @Test
    @DisplayName("FR-PAY-03 explicit allocations are checked against outstanding and the payment")
    void explicit_checkedAgainstOutstandingAndPayment() {
        Obligation x = item("ORD-2026-0001 line 1", "10000.00");
        Obligation y = item("ORD-2026-0002 line 1", "8000.00");
        Map<UUID, Obligation> open = Map.of(x.targetId(), x, y.targetId(), y);

        // AC1: 10000 + 5000 of a 20000 payment -> stored as asked, 5000 left as advance
        List<PlannedAllocation> ok = AllocationPlanner.explicit(new BigDecimal("20000.00"),
                List.of(alloc(x, "10000.00"), alloc(y, "5000.00")), open);
        assertThat(ok).hasSize(2);
        // AC2: above the item's outstanding; AC3: above the payment
        assertThatThrownBy(() -> AllocationPlanner.explicit(new BigDecimal("20000.00"),
                List.of(alloc(x, "10000.01")), open)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PAYMENT_ALLOCATION_EXCEEDED));
        assertThatThrownBy(() -> AllocationPlanner.explicit(new BigDecimal("12000.00"),
                List.of(alloc(x, "10000.00"), alloc(y, "5000.00")), open)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PAYMENT_ALLOCATION_EXCEEDED));
    }

    private static Obligation item(String label, String outstanding) {
        return new Obligation(AllocationTargetType.ORDER_ITEM, UUID.randomUUID(), DAY, label, new BigDecimal(outstanding));
    }

    private static PlannedAllocation alloc(Obligation o, String amount) {
        return new PlannedAllocation(o.targetType(), o.targetId(), new BigDecimal(amount));
    }
}
