package com.nexora.procurement.domain;

// How a purchase is paid: at once (CASH, due on the purchase date) or later (CREDIT, due after the credit days).
public enum PaymentType {
    CASH,
    CREDIT
}
