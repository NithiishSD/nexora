package com.nexora.parties.domain;

import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;

// An inactive party cannot be used in a new transaction (its history stays visible). 422.
public class PartyInactiveException extends BusinessException {

    public PartyInactiveException(String partyCode) {
        super(ErrorCode.PARTY_INACTIVE, "Party " + partyCode + " is inactive.");
    }
}
