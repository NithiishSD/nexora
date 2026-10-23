package com.nexora.outsourcing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.inventory.domain.ProductType;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.error.InvalidProductForOperationException;

class OutsourcingJobTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 8);

    @Test
    @DisplayName("FR-OUT-03 receive beyond issued throws outsource receipt exceeded")
    void receive_beyondIssued_throwsOutsourceReceiptExceeded() {
        OutsourcingJob job = issued("200.000");
        job.receive(kg("150.000"), kg("3.000"), DAY);
        assertThat(job.getStatus()).isEqualTo(JobStatus.PARTIALLY_RECEIVED);
        assertThat(job.outsideWeightKg()).isEqualByComparingTo("47.000");

        // AC2: 60 + 0 > the 47 still outside; nothing changes
        assertThatThrownBy(() -> job.receive(kg("60.000"), kg("0.000"), DAY.plusDays(1)))
                .isInstanceOf(OutsourceReceiptExceededException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ErrorCode.OUTSOURCE_RECEIPT_EXCEEDED));
        assertThat(job.getReceivedWeightKg()).isEqualByComparingTo("150.000");
        assertThat(job.getReceivedDate()).isEqualTo(DAY);
        // 45 + 3 = 48 is also one kg too much
        assertThatThrownBy(() -> job.receive(kg("45.000"), kg("3.000"), DAY))
                .isInstanceOf(OutsourceReceiptExceededException.class);
        // AC3: a CREATED job takes no receipt
        assertThatThrownBy(() -> created("10.000").receive(kg("1.000"), kg("0.000"), DAY))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    @DisplayName("FR-OUT-04 NFR-TEST-08 received plus wastage equals issued completes job")
    void receivedPlusWastageEqualsIssued_completesJob() {
        OutsourcingJob job = issued("200.000");
        job.receive(kg("150.000"), kg("3.000"), DAY);
        job.receive(kg("45.000"), kg("2.000"), DAY.plusDays(2));

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getReceivedWeightKg()).isEqualByComparingTo("195.000");
        assertThat(job.getWastageWeightKg()).isEqualByComparingTo("5.000");
        assertThat(job.outsideWeightKg()).isEqualByComparingTo("0.000");
        assertThat(job.getReceivedDate()).isEqualTo(DAY.plusDays(2));
        // FR-OUT-05: payable = received x the historical rate
        assertThat(job.payableAmount()).isEqualByComparingTo("23400.00");
        // AC2: a completed job takes no further receipt
        assertThatThrownBy(() -> job.receive(kg("0.001"), kg("0.000"), DAY))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OUTSOURCE_RECEIPT_EXCEEDED));
        // one receipt for everything completes straight from MATERIAL_ISSUED
        OutsourcingJob once = issued("10.000");
        once.receive(kg("9.500"), kg("0.500"), DAY);
        assertThat(once.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    @DisplayName("FR-OUT-06 cancel after issue throws invalid state transition")
    void cancel_afterIssue_throwsInvalidStateTransition() {
        assertThatThrownBy(() -> issued("10.000").cancel())
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        OutsourcingJob job = created("10.000");
        job.cancel();
        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.outsideWeightKg()).isEqualByComparingTo("0.000");
        assertThatThrownBy(() -> job.issue(DAY)).isInstanceOf(BusinessException.class);
        // FR-INV-10: vuda warp is never made, raw silk is the input
        assertThatThrownBy(() -> OutsourcingJob.create("JOB-2026-0009", UUID.randomUUID(), ProductType.VUDA_WARP,
                kg("1.000"), new BigDecimal("1.00"), null, List.of()))
                .isInstanceOf(InvalidProductForOperationException.class);
    }

    private static OutsourcingJob created(String issuedKg) {
        return OutsourcingJob.create("JOB-2026-0001", UUID.randomUUID(), ProductType.KORA_WARP, kg(issuedKg),
                new BigDecimal("120.00"), null, List.of());
    }

    private static OutsourcingJob issued(String issuedKg) {
        OutsourcingJob job = created(issuedKg);
        job.issue(DAY);
        return job;
    }

    private static BigDecimal kg(String value) {
        return new BigDecimal(value);
    }
}
