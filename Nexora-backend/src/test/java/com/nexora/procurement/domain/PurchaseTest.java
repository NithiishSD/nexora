package com.nexora.procurement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.inventory.domain.ProductType;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.error.FieldValidationException;

// Plain unit tests of the purchase state machine and its rules: no Spring, no database.
class PurchaseTest {

    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);

    @Test
    @DisplayName("FR-PROC-08 record receipt beyond ordered throws purchase receipt exceeded")
    void recordReceipt_beyondOrdered_throwsPurchaseReceiptExceeded() {
        Purchase purchase = purchase("250.000", "800.00");
        receive(purchase, "180.000", "180.000", "0.000");

        // 180 of 250 received: 100 more is too much, and nothing changes
        assertThatThrownBy(() -> receive(purchase, "100.000", "100.000", "0.000"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.PURCHASE_RECEIPT_EXCEEDED);
                    assertThat(e.getMessage()).contains("70.000");
                });
        assertThat(purchase.receivedWeightKg()).isEqualByComparingTo("180.000");
        assertThat(purchase.getReceipts()).hasSize(1);
    }

    @Test
    @DisplayName("FR-PROC-09 record receipt accepted plus rejected above received is rejected")
    void recordReceipt_acceptedPlusRejectedAboveReceived_isRejected() {
        Purchase purchase = purchase("250.000", "800.00");

        // AC2: 90 + 20 > 100
        assertThatThrownBy(() -> receive(purchase, "100.000", "90.000", "20.000"))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(e.getField()).isEqualTo("acceptedWeightKg"));
        // AC3: negative rejected weight
        assertThatThrownBy(() -> receive(purchase, "100.000", "90.000", "-1.000"))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(e.getField()).isEqualTo("rejectedWeightKg"));
        // FR-PROC-08 AC4: received 0
        assertThatThrownBy(() -> receive(purchase, "0.000", "0.000", "0.000"))
                .isInstanceOf(FieldValidationException.class);
        assertThat(purchase.getReceipts()).isEmpty();
        // AC1: 95 + 5 = 100 is fine; a part may stay unclassified (95 + 0 <= 100) too
        receive(purchase, "100.000", "95.000", "5.000");
        receive(purchase, "10.000", "9.000", "0.000");
        assertThat(purchase.acceptedWeightKg()).isEqualByComparingTo("104.000");
        assertThat(purchase.rejectedWeightKg()).isEqualByComparingTo("5.000");
    }

    @Test
    @DisplayName("FR-PROC-11 NFR-TEST-08 record receipt status moves partial then full")
    void recordReceipt_statusMovesPartialThenFull() {
        Purchase purchase = purchase("250.000", "800.00");
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.ORDERED);

        receive(purchase, "80.000", "80.000", "0.000");
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.PARTIALLY_RECEIVED);
        assertThat(purchase.remainingWeightKg()).isEqualByComparingTo("170.000");
        receive(purchase, "100.000", "100.000", "0.000");
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.PARTIALLY_RECEIVED);
        receive(purchase, "70.000", "70.000", "0.000");                   // exactly the rest
        assertThat(purchase.getStatus()).isEqualTo(PurchaseStatus.FULLY_RECEIVED);
        assertThat(purchase.remainingWeightKg()).isEqualByComparingTo("0.000");

        // FR-PROC-11 AC3: a fully received purchase takes no further receipt of any weight
        assertThatThrownBy(() -> receive(purchase, "0.001", "0.001", "0.000"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PURCHASE_RECEIPT_EXCEEDED));
        // ORDERED -> FULLY_RECEIVED in one receipt is legal too
        Purchase single = purchase("50.000", "800.00");
        receive(single, "50.000", "50.000", "0.000");
        assertThat(single.getStatus()).isEqualTo(PurchaseStatus.FULLY_RECEIVED);
    }

    @Test
    @DisplayName("FR-PROC-12 NFR-TEST-08 cancel after receipt throws invalid state transition")
    void cancel_afterReceipt_throwsInvalidStateTransition() {
        Purchase received = purchase("250.000", "800.00");
        receive(received, "10.000", "10.000", "0.000");
        assertCode(received::cancel, ErrorCode.INVALID_STATE_TRANSITION);
        assertThat(received.getStatus()).isEqualTo(PurchaseStatus.PARTIALLY_RECEIVED);

        // ORDERED -> CANCELLED is legal once; a cancelled purchase can neither be cancelled again nor received
        Purchase open = purchase("250.000", "800.00");
        open.cancel();
        assertThat(open.getStatus()).isEqualTo(PurchaseStatus.CANCELLED);
        assertThat(open.remainingWeightKg()).isEqualByComparingTo("0.000");
        assertCode(open::cancel, ErrorCode.INVALID_STATE_TRANSITION);
        assertCode(() -> receive(open, "10.000", "10.000", "0.000"), ErrorCode.INVALID_STATE_TRANSITION);
        assertCode(() -> open.recordReturn("RTN-1", OCT_1, BigDecimal.ONE, "x", null), ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("FR-PROC-04 due date is purchase date plus credit days")
    void dueDate_isPurchaseDatePlusCreditDays() {
        Purchase credit = Purchase.create("PUR-1", UUID.randomUUID(), ProductType.RAW_SILK, OCT_1,
                new BigDecimal("250.000"), new BigDecimal("800.00"), PaymentType.CREDIT, 10, null, null);
        assertThat(credit.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 11));

        Purchase cash = Purchase.create("PUR-2", UUID.randomUUID(), ProductType.RAW_SILK, OCT_1,
                new BigDecimal("250.000"), new BigDecimal("800.00"), PaymentType.CASH, 0, null, null);
        assertThat(cash.getDueDate()).isEqualTo(OCT_1);
        // a cash purchase cannot carry credit days, and credit days are never negative
        assertThatThrownBy(() -> Purchase.create("PUR-3", UUID.randomUUID(), ProductType.RAW_SILK, OCT_1,
                new BigDecimal("1"), new BigDecimal("1"), PaymentType.CASH, 5, null, null))
                .isInstanceOfSatisfying(FieldValidationException.class, e -> assertThat(e.getField()).isEqualTo("creditDays"));
        assertThatThrownBy(() -> Purchase.create("PUR-4", UUID.randomUUID(), ProductType.RAW_SILK, OCT_1,
                new BigDecimal("1"), new BigDecimal("1"), PaymentType.CREDIT, -3, null, null))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    @DisplayName("FR-PROC-03 ordered value is rounded half up")
    void orderedValue_isRoundedHalfUp() {
        assertThat(purchase("250.000", "800.00").orderedValue()).isEqualByComparingTo("200000.00");
        // 12.345 x 10.01 = 123.57345 -> 123.57; 0.005 x 1.00 = 0.005 -> 0.01 (HALF_UP)
        assertThat(purchase("12.345", "10.01").orderedValue()).isEqualByComparingTo("123.57");
        assertThat(purchase("0.005", "1.00").orderedValue()).isEqualByComparingTo("0.01");
        assertThat(purchase("0.005", "1.00").orderedValue().scale()).isEqualTo(2);
        // weights with 4 decimals and silk warp purchases are rejected
        assertThatThrownBy(() -> purchase("12.3456", "1.00")).isInstanceOf(FieldValidationException.class);
        assertCode(() -> Purchase.create("PUR-5", UUID.randomUUID(), ProductType.SILK_WARP, OCT_1,
                new BigDecimal("1"), new BigDecimal("1"), PaymentType.CASH, 0, null, null),
                ErrorCode.INVALID_PRODUCT_FOR_OPERATION);
    }

    @Test
    @DisplayName("FR-PROC-13 FR-PROC-15 record return beyond returnable throws insufficient inventory")
    void recordReturn_beyondReturnable_throwsInsufficientInventory() {
        Purchase purchase = purchase("250.000", "800.00");
        receive(purchase, "100.000", "100.000", "0.000");
        purchase.recordReturn("RTN-1", OCT_1, new BigDecimal("60.000"), "moisture", null);

        // FR-PROC-13 AC2: 100 accepted, 60 returned -> only 40 returnable
        assertThatThrownBy(() -> purchase.recordReturn("RTN-2", OCT_1, new BigDecimal("50.000"), "moisture", null))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_INVENTORY);
                    assertThat(e.getMessage()).contains("40.000");
                });
        assertThat(purchase.returnedWeightKg()).isEqualByComparingTo("60.000");
        // the receipt keeps its figures (FR-PROC-14) and the payable falls by the returned kg (FR-PROC-15 AC1 shape)
        assertThat(purchase.getReceipts().get(0).getAcceptedWeightKg()).isEqualByComparingTo("100.000");
        assertThat(purchase.payableAmount()).isEqualByComparingTo("32000.00");
    }

    private static Purchase purchase(String kg, String rate) {
        return Purchase.create("PUR-TEST-0001", UUID.randomUUID(), ProductType.RAW_SILK, OCT_1, new BigDecimal(kg),
                new BigDecimal(rate), PaymentType.CREDIT, 10, null, null);
    }

    private static void receive(Purchase purchase, String received, String accepted, String rejected) {
        purchase.recordReceipt("RCT-TEST", OCT_1, new BigDecimal(received), new BigDecimal(accepted),
                new BigDecimal(rejected), null);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }
}
