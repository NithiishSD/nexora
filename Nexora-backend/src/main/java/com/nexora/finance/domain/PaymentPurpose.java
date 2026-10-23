package com.nexora.finance.domain;

import java.util.Set;

import com.nexora.parties.domain.PartyRole;

// Who is paid or paying: selects the party role, the direction, the ledger type and which obligations an
// allocation may target (05-API-SPEC section 9).
public enum PaymentPurpose {
    CUSTOMER(PartyRole.CUSTOMER, PaymentDirection.RECEIVED, TransactionType.CUSTOMER_PAYMENT,
            Set.of(AllocationTargetType.ORDER_ITEM, AllocationTargetType.OPENING_OBLIGATION)),
    SUPPLIER(PartyRole.SUPPLIER, PaymentDirection.PAID, TransactionType.SUPPLIER_PAYMENT,
            Set.of(AllocationTargetType.PURCHASE, AllocationTargetType.OPENING_OBLIGATION)),
    MANUFACTURER(PartyRole.MANUFACTURER, PaymentDirection.PAID, TransactionType.MANUFACTURER_PAYMENT,
            Set.of(AllocationTargetType.OUTSOURCING_JOB, AllocationTargetType.OPENING_OBLIGATION)),
    WORKER(PartyRole.WORKER, PaymentDirection.PAID, TransactionType.WORKER_PAYMENT,
            Set.of(AllocationTargetType.WORK_RECORD, AllocationTargetType.OPENING_OBLIGATION));

    private final PartyRole role;
    private final PaymentDirection direction;
    private final TransactionType transactionType;
    private final Set<AllocationTargetType> targets;

    PaymentPurpose(PartyRole role, PaymentDirection direction, TransactionType transactionType,
                   Set<AllocationTargetType> targets) {
        this.role = role;
        this.direction = direction;
        this.transactionType = transactionType;
        this.targets = targets;
    }

    public PartyRole role() {
        return role;
    }

    public PaymentDirection direction() {
        return direction;
    }

    public TransactionType transactionType() {
        return transactionType;
    }

    // Money received goes IN to the account, money paid goes OUT.
    public Direction ledgerDirection() {
        return direction == PaymentDirection.RECEIVED ? Direction.IN : Direction.OUT;
    }

    public boolean allows(AllocationTargetType target) {
        return targets.contains(target);
    }
}
