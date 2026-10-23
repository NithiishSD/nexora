package com.nexora.finance.domain;

// The fixed category list of the first release (FR-FIN-11); configurable categories are post-MVP (FR-FIN-19). An
// unknown value in a request is 400 MALFORMED_REQUEST.
public enum ExpenseCategory {
    TRANSPORT,
    ELECTRICITY,
    RENT,
    WORKER,
    MAINTENANCE,
    PACKING,
    OTHER
}
