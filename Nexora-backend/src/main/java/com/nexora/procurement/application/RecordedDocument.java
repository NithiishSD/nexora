package com.nexora.procurement.application;

import java.util.UUID;

import com.nexora.procurement.api.dto.PurchaseResponse;

// Result of recording a receipt or a return: the new record's id (for the Location header) and the
// purchase as it looks afterwards (the response body, E-PROC-06/07).
public record RecordedDocument(UUID id, PurchaseResponse purchase) {
}
