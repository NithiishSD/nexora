package com.nexora.parties.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.error.FieldValidationException;
import com.nexora.shared.error.InvalidStateTransitionException;

// Plain unit tests of the Party rules (no Spring, no database).
class PartyTest {

    private static Party customer() {
        return Party.create("PTY-2026-0001", PartyDetails.named("Kumar Silks"), Set.of(PartyRole.CUSTOMER), List.of());
    }

    @Test
    @DisplayName("FR-PARTY-01 create without role is rejected")
    void create_withoutRole_isRejected() {
        assertThatThrownBy(() -> Party.create("PTY-2026-0001", PartyDetails.named("X"), Set.of(), List.of()))
                .isInstanceOfSatisfying(FieldValidationException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.getField()).isEqualTo("roles");
                });
    }

    @Test
    @DisplayName("FR-PARTY-02 FR-PARTY-16 create with several roles keeps all")
    void create_withSeveralRoles_keepsAll() {
        Party party = Party.create("PTY-2026-0002", PartyDetails.named("SBI"),
                List.of(PartyRole.SUPPLIER, PartyRole.CUSTOMER, PartyRole.LENDER, PartyRole.CUSTOMER), List.of());

        assertThat(party.getRoles()).containsExactly(PartyRole.CUSTOMER, PartyRole.LENDER, PartyRole.SUPPLIER);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
    }

    @Test
    @DisplayName("FR-PARTY-04 replace phones with two primaries is rejected")
    void replacePhones_withTwoPrimaries_isRejected() {
        Party party = customer();

        assertThatThrownBy(() -> party.replacePhones(List.of(
                PartyPhoneNumber.of("9840012345", "mobile", true),
                PartyPhoneNumber.of("9840054321", "WhatsApp", true))))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    @DisplayName("FR-PARTY-04 replace phones without primary makes first primary")
    void replacePhones_withoutPrimary_makesFirstPrimary() {
        Party party = customer();

        party.replacePhones(List.of(
                PartyPhoneNumber.of("1111111111", "mobile", false),
                PartyPhoneNumber.of("2222222222", "home", false),
                PartyPhoneNumber.of("3333333333", "shop", false)));

        assertThat(party.getPhones()).extracting(PartyPhoneNumber::isPrimary).containsExactly(true, false, false);
        assertThat(party.primaryPhone()).isEqualTo("1111111111");
    }

    @Test
    @DisplayName("FR-PARTY-10 NFR-TEST-08 deactivate when inactive throws invalid state transition")
    void deactivate_whenInactive_throwsInvalidStateTransition() {
        Party party = customer();
        party.deactivate();

        assertThatThrownBy(party::deactivate).isInstanceOf(InvalidStateTransitionException.class);
        party.activate();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
        assertThatThrownBy(party::activate).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("FR-PARTY-13 supplier credit days without supplier role is rejected")
    void supplierCreditDays_withoutSupplierRole_isRejected() {
        PartyDetails withCreditDays = new PartyDetails("X", null, null, null, null, null, null, null, 10, null, null);

        assertThatThrownBy(() -> Party.create("PTY-2026-0003", withCreditDays, Set.of(PartyRole.CUSTOMER), List.of()))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(e.getField()).isEqualTo("defaultCreditDays"));
        Party supplier = Party.create("PTY-2026-0004", withCreditDays, Set.of(PartyRole.SUPPLIER), List.of());
        assertThat(supplier.getDefaultCreditDays()).isEqualTo(10);
    }

    @Test
    @DisplayName("FR-PARTY-14 worker rates without worker role is rejected")
    void workerRates_withoutWorkerRole_isRejected() {
        PartyDetails withRate = new PartyDetails("X", null, null, null, null, null, null, null, null,
                new BigDecimal("100"), new BigDecimal("2.5"));

        assertThatThrownBy(() -> Party.create("PTY-2026-0005", withRate, Set.of(PartyRole.SUPPLIER), List.of()))
                .isInstanceOf(FieldValidationException.class);
        Party worker = Party.create("PTY-2026-0006", withRate, Set.of(PartyRole.WORKER), List.of());
        assertThat(worker.getDefaultRollingRatePerHour()).isEqualByComparingTo("100.00");
        assertThat(worker.getDefaultRollingRatePerHour().scale()).isEqualTo(2);   // stored as money (2 decimals)
        assertThat(worker.getDefaultWarpingRatePerKg()).isEqualByComparingTo("2.50");
    }

    @Test
    @DisplayName("FR-PARTY-09 add role to existing party keeps existing roles")
    void addRole_toExistingParty_keepsExistingRoles() {
        Party party = customer();

        party.addRole(PartyRole.SUPPLIER);
        party.addRole(PartyRole.SUPPLIER);              // adding twice is a no-op

        assertThat(party.getRoles()).containsExactly(PartyRole.CUSTOMER, PartyRole.SUPPLIER);
    }

    @Test
    @DisplayName("FR-PARTY-11 FR-PARTY-12 require usable as checks role then status")
    void requireUsableAs_checksRoleAndStatus() {
        Party party = customer();

        assertThatThrownBy(() -> party.requireUsableAs(PartyRole.SUPPLIER)).isInstanceOf(InvalidPartyRoleException.class);
        party.requireUsableAs(PartyRole.CUSTOMER);      // no exception
        party.deactivate();
        assertThatThrownBy(() -> party.requireUsableAs(PartyRole.CUSTOMER)).isInstanceOf(PartyInactiveException.class);
    }

    @Test
    @DisplayName("FR-PARTY-17 customer credit days validated and updated")
    void customerCreditDays_validatedAndUpdated() {
        Party party = customer();
        assertThat(party.getCustomerCreditDays()).isNull();

        party.updateCustomerTerms(30);
        assertThat(party.getCustomerCreditDays()).isEqualTo(30);

        party.updateCustomerTerms(null);
        assertThat(party.getCustomerCreditDays()).isNull();

        assertThatThrownBy(() -> party.updateCustomerTerms(-1))
                .isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> party.updateCustomerTerms(366))
                .isInstanceOf(FieldValidationException.class);

        Party supplier = Party.create("PTY-2026-0007", PartyDetails.named("Supplier"), Set.of(PartyRole.SUPPLIER), List.of());
        assertThatThrownBy(() -> supplier.updateCustomerTerms(30))
                .isInstanceOf(FieldValidationException.class);
    }
}
