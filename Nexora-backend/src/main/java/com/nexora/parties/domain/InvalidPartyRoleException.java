package com.nexora.parties.domain;

import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;

// The party exists but lacks the role the operation needs (e.g. a purchase from a party that is not a supplier). 422.
public class InvalidPartyRoleException extends BusinessException {

    public InvalidPartyRoleException(String partyCode, PartyRole requiredRole) {
        super(ErrorCode.INVALID_PARTY_ROLE, "Party " + partyCode + " does not have the role " + requiredRole + ".");
    }
}
