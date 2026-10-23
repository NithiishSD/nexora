package com.nexora.parties.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.nexora.parties.api.dto.PartyResponse;
import com.nexora.parties.domain.PartyRole;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.support.AbstractIntegrationTest;
import com.nexora.support.TestDataFactory;

// The contract that procurement, orders, outsourcing, workforce, finance and loans rely on.
class PartyServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    PartyService partyService;

    private TestDataFactory data() {
        return new TestDataFactory(partyService);
    }

    @Test
    @DisplayName("FR-PARTY-11 require active with role inactive party throws party inactive")
    void requireActiveWithRole_inactiveParty_throwsPartyInactive() {
        PartyResponse supplier = data().supplier();
        partyService.deactivate(supplier.id());

        assertThatThrownBy(() -> partyService.requireActiveWithRole(supplier.id(), PartyRole.SUPPLIER))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARTY_INACTIVE));
        // AC2: the party itself (its history) is still readable
        assertThat(partyService.getRef(supplier.id()).partyCode()).isEqualTo(supplier.partyCode());
    }

    @Test
    @DisplayName("FR-PARTY-12 require active with role missing role throws invalid party role")
    void requireActiveWithRole_missingRole_throwsInvalidPartyRole() {
        PartyResponse customer = data().customer();
        PartyResponse worker = data().worker();

        assertThatThrownBy(() -> partyService.requireActiveWithRole(customer.id(), PartyRole.SUPPLIER))   // AC1
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_PARTY_ROLE));
        assertThatThrownBy(() -> partyService.requireActiveWithRole(worker.id(), PartyRole.CUSTOMER))     // AC2
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_PARTY_ROLE));
        assertThatThrownBy(() -> partyService.requireActiveWithRole(UUID.randomUUID(), PartyRole.CUSTOMER))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("FR-PARTY-16 require active with role lender role is accepted")
    void requireActiveWithRole_lenderRole_isAccepted() {
        PartyResponse lender = data().party("SBI", List.of(PartyRole.LENDER), null, null);

        PartyRef ref = partyService.requireActiveWithRole(lender.id(), PartyRole.LENDER);

        assertThat(ref.id()).isEqualTo(lender.id());
        assertThat(ref.partyCode()).isEqualTo(lender.partyCode());
        assertThat(ref.name()).startsWith("SBI");
        // FR-PARTY-16 AC2: a customer-only party is not a lender
        assertThatThrownBy(() -> partyService.requireActiveWithRole(data().customer().id(), PartyRole.LENDER))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_PARTY_ROLE));
    }
}
