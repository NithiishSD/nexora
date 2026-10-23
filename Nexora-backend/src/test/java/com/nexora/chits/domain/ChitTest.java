package com.nexora.chits.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;

class ChitTest {

    @Test
    @DisplayName("FR-CHIT-09 NFR-TEST-08 payout reversal moves matured back to active only")
    void payoutReversal_movesMaturedBackToActiveOnly() {
        Chit chit = chit();
        chit.receivePayout();
        assertThat(chit.getStatus()).isEqualTo(ChitStatus.MATURED);
        // a second payout while MATURED is refused
        assertThatThrownBy(chit::receivePayout).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));

        // MATURED -> ACTIVE, and the payout can be recorded again
        chit.payoutReversed();
        assertThat(chit.getStatus()).isEqualTo(ChitStatus.ACTIVE);
        chit.receivePayout();

        // a CLOSED chit stays CLOSED; an ACTIVE one stays ACTIVE
        chit.close();
        chit.payoutReversed();
        assertThat(chit.getStatus()).isEqualTo(ChitStatus.CLOSED);
        Chit active = chit();
        active.payoutReversed();
        assertThat(active.getStatus()).isEqualTo(ChitStatus.ACTIVE);
        // closed chits take nothing more and cannot close twice
        assertThatThrownBy(chit::requireOpen).isInstanceOf(BusinessException.class);
        assertThatThrownBy(chit::close).isInstanceOf(BusinessException.class);
        assertThatThrownBy(chit::receivePayout).isInstanceOf(BusinessException.class);
    }

    private static Chit chit() {
        return Chit.create("CHT-2026-0001", "Chit A", new BigDecimal("500000.00"), new BigDecimal("25000.00"), 20,
                LocalDate.of(2026, 4, 5), null, null, null);
    }
}
