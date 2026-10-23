package com.nexora.orders.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

// A production batch or outsourcing job serving the order (detail only): its number, status and the kg meant for
// this order.
public record LinkedWorkResponse(UUID id, String batchNumber, String jobNumber, String status,
                                 BigDecimal allocatedWeightKg) {
}
