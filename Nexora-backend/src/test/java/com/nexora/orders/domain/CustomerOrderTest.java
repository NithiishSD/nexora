package com.nexora.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.inventory.domain.ProductType;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.error.FieldValidationException;

class CustomerOrderTest {

    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 10, 7);

    @Test
    @DisplayName("FR-ORD-02 item amount is rounded half up to paise")
    void itemAmount_isRoundedHalfUpToPaise() {
        // AC1: 12.345 kg x 333.33 = 4114.95885 -> 4114.96
        CustomerOrder order = order(line(ProductType.KORA_WARP, "100.000", "850.00"),
                line(ProductType.SILK_WARP, "12.345", "333.33"));
        assertThat(order.getItems().get(1).getAmount()).isEqualByComparingTo("4114.96");
        // AC2: the total is the sum of the rounded item amounts
        assertThat(order.totalAmount()).isEqualByComparingTo("89114.96");
        // a half paisa rounds up: 0.005 -> 0.01
        assertThat(OrderItem.amountOf(new BigDecimal("0.001"), new BigDecimal("5.00"))).isEqualByComparingTo("0.01");
        // AC4: four decimals of weight are rejected, never rounded away
        assertThatThrownBy(() -> order(line(ProductType.KORA_WARP, "12.3456", "1.00")))
                .isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> order(line(ProductType.KORA_WARP, "0.000", "1.00")))
                .isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> order()).isInstanceOf(FieldValidationException.class);
        // lines are numbered from 1
        assertThat(order.getItems()).extracting(OrderItem::getLineNo).containsExactly(1, 2);
    }

    @Test
    @DisplayName("FR-ORD-07 FR-ORD-10 FR-ORD-13 NFR-TEST-08 transitions follow state machine")
    void transitions_followStateMachine() {
        // the production path
        CustomerOrder order = order(line(ProductType.SILK_WARP, "10.000", "100.00"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getStatus().isObligation()).isFalse();
        order.confirm();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getStatus().isObligation()).isTrue();
        order.startWork();
        assertThat(order.isLocked()).isTrue();
        order.markReady();
        assertThat(order.getItems()).allSatisfy(i -> assertThat(i.getStatus()).isEqualTo(OrderItemStatus.READY));
        order.deliver(ORDER_DATE.plusDays(3));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getDeliveredOn()).isEqualTo(ORDER_DATE.plusDays(3));
        assertThat(order.getItems()).allSatisfy(i -> assertThat(i.getStatus()).isEqualTo(OrderItemStatus.DELIVERED));
        order.complete();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getStatus().isObligation()).isTrue();

        // filled from existing stock: CONFIRMED -> READY (plan decision R5)
        CustomerOrder fromStock = order(line(ProductType.RAW_SILK, "5.000", "900.00"));
        fromStock.confirm();
        fromStock.markReady();
        assertThat(fromStock.getStatus()).isEqualTo(OrderStatus.READY);

        // cancel from PLACED and from CONFIRMED; the items are cancelled with the order
        CustomerOrder placed = order(line(ProductType.RAW_SILK, "5.000", "900.00"));
        placed.cancel(false);
        assertThat(placed.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(placed.getItems()).allSatisfy(i -> assertThat(i.getStatus()).isEqualTo(OrderItemStatus.CANCELLED));
        assertThat(placed.getStatus().isObligation()).isFalse();
        CustomerOrder confirmed = order(line(ProductType.RAW_SILK, "5.000", "900.00"));
        confirmed.confirm();
        confirmed.cancel(false);
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("NFR-TEST-08 illegal transition throws invalid state transition")
    void illegalTransition_throwsInvalidStateTransition() {
        // PLACED cannot skip confirmation
        expectCode(ErrorCode.INVALID_STATE_TRANSITION, CustomerOrder::markReady);
        expectCode(ErrorCode.INVALID_STATE_TRANSITION, o -> o.deliver(ORDER_DATE));
        expectCode(ErrorCode.INVALID_STATE_TRANSITION, CustomerOrder::complete);
        expectCode(ErrorCode.INVALID_STATE_TRANSITION, CustomerOrder::startWork);
        // FR-ORD-07 AC2: a delivered order cannot be confirmed; FR-ORD-13 AC2: READY cannot complete
        expectCode(ErrorCode.INVALID_STATE_TRANSITION, o -> {
            o.confirm();
            o.markReady();
            o.complete();
        });
        expectCode(ErrorCode.INVALID_STATE_TRANSITION, o -> {
            o.confirm();
            o.markReady();
            o.deliver(ORDER_DATE);
            o.confirm();
        });
        // FR-ORD-11 AC3: CONFIRMED but not ready cannot be delivered
        expectCode(ErrorCode.INVALID_STATE_TRANSITION, o -> {
            o.confirm();
            o.deliver(ORDER_DATE);
        });
        // FR-ORD-09 AC2/AC4: allocations or a delivered order block the cancel
        expectCode(ErrorCode.INVALID_STATE_TRANSITION, o -> {
            o.confirm();
            o.cancel(true);
        });
        expectCode(ErrorCode.INVALID_STATE_TRANSITION, o -> {
            o.confirm();
            o.markReady();
            o.deliver(ORDER_DATE);
            o.cancel(false);
        });
        // a cancelled order stays cancelled
        expectCode(ErrorCode.INVALID_STATE_TRANSITION, o -> {
            o.cancel(false);
            o.cancel(false);
        });
        expectCode(ErrorCode.INVALID_STATE_TRANSITION, o -> {
            o.cancel(false);
            o.confirm();
        });
    }

    @Test
    @DisplayName("FR-ORD-08 edit after lock throws order locked")
    void edit_afterLock_throwsOrderLocked() {
        List<OrderLine> newLines = List.of(line(ProductType.KORA_WARP, "110.000", "850.00"));
        // unlocked: the edit recomputes the amount
        CustomerOrder order = order(line(ProductType.KORA_WARP, "100.000", "850.00"));
        order.edit(null, null, newLines, false);
        assertThat(order.totalAmount()).isEqualByComparingTo("93500.00");
        assertThat(order.canModify(false)).isTrue();

        // AC2: in progress -> ORDER_LOCKED for edit and cancel
        order.confirm();
        order.startWork();
        assertThat(order.canModify(false)).isFalse();
        expectLocked(() -> order.edit(null, null, newLines, false));
        expectLocked(() -> order.cancel(false));
        assertThat(order.totalAmount()).isEqualByComparingTo("93500.00");

        // FR-ORD-06 AC3: allocations lock the order against edits even while CONFIRMED
        CustomerOrder paid = order(line(ProductType.KORA_WARP, "100.000", "850.00"));
        paid.confirm();
        assertThat(paid.canModify(true)).isFalse();
        expectLocked(() -> paid.edit(null, null, newLines, true));

        // a required date before the order date is a field error
        CustomerOrder other = order(line(ProductType.KORA_WARP, "1.000", "1.00"));
        assertThatThrownBy(() -> other.edit(ORDER_DATE.minusDays(1), null, null, false))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(e.getField()).isEqualTo("requiredDate"));
        // fewer lines remove the surplus ones
        CustomerOrder two = order(line(ProductType.KORA_WARP, "1.000", "1.00"), line(ProductType.SILK_WARP, "2.000", "1.00"));
        two.edit(null, null, List.of(line(ProductType.SILK_WARP, "3.000", "2.00")), false);
        assertThat(two.getItems()).singleElement().satisfies(i -> {
            assertThat(i.getLineNo()).isEqualTo(1);
            assertThat(i.getAmount()).isEqualByComparingTo("6.00");
        });
    }

    @Test
    @DisplayName("FR-ORD-16 late only while not delivered and not cancelled")
    void late_onlyWhileUndelivered() {
        CustomerOrder order = CustomerOrder.place("ORD-2026-0001", UUID.randomUUID(), ORDER_DATE,
                LocalDate.of(2026, 10, 10), null, List.of(line(ProductType.RAW_SILK, "1.000", "1.00")));
        LocalDate today = LocalDate.of(2026, 10, 12);
        assertThat(order.isLate(today)).isTrue();
        assertThat(order.isLate(LocalDate.of(2026, 10, 10))).isFalse();
        order.cancel(false);
        assertThat(order.isLate(today)).isFalse();
    }

    private static void expectLocked(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ORDER_LOCKED));
    }

    private static void expectCode(ErrorCode code, Consumer<CustomerOrder> action) {
        CustomerOrder order = order(line(ProductType.RAW_SILK, "1.000", "1.00"));
        assertThatThrownBy(() -> action.accept(order)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    private static CustomerOrder order(OrderLine... lines) {
        return CustomerOrder.place("ORD-2026-0001", UUID.randomUUID(), ORDER_DATE, null, null, List.of(lines));
    }

    private static OrderLine line(ProductType product, String kg, String rate) {
        return new OrderLine(product, new BigDecimal(kg), new BigDecimal(rate), null, null);
    }
}
