package com.nexora.parties.domain;

// What a party can be used for. One party may hold several roles (a supplier who is also a customer).
// Values match the CHECK constraint ck_party_roles_role (migration V3).
public enum PartyRole {
    CUSTOMER,       // places orders
    SUPPLIER,       // sells us raw silk / vuda warp
    MANUFACTURER,   // outside job worker who makes warp from our silk
    WORKER,         // in-house rolling / warping worker
    LENDER          // bank or private person who lends money
}
