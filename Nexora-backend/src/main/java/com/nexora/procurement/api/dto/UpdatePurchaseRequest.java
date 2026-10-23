package com.nexora.procurement.api.dto;

import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

// Body of PATCH /api/v1/purchases/{id} (E-PROC-04). Only notes and the bill reference may change: a field that is
// missing (null) keeps its value, an empty string clears it. The other fields are listed only so that sending
// one is a clear 400 naming it (FR-PROC-05 AC2) instead of being silently ignored: the rate and weight of a
// purchase are history. A wrong purchase is cancelled and created again.
public record UpdatePurchaseRequest(
        @Size(max = 1000) String notes,
        @Size(max = 100) String billReference,
        @Null(message = HISTORY) Object ratePerKg,
        @Null(message = HISTORY) Object orderedWeightKg,
        @Null(message = HISTORY) Object supplierId,
        @Null(message = HISTORY) Object purchaseDate,
        @Null(message = HISTORY) Object creditDays,
        @Null(message = HISTORY) Object paymentType,
        @Null(message = HISTORY) Object productType) {

    public static final String HISTORY = "cannot be changed; cancel the purchase and create a new one";
}
