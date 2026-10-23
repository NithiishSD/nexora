package com.nexora.orders.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.nexora.orders.domain.OrderStatus;
import com.nexora.parties.application.PartyRef;
import com.nexora.shared.domain.PaymentStatus;

// Full view of one order (05-API-SPEC section 8). Every figure below the header is derived by the backend.
public record OrderResponse(
        UUID id,
        String orderNumber,
        PartyRef customer,
        LocalDate orderDate,
        LocalDate requiredDate,
        LocalDate deliveredOn,
        OrderStatus status,
        boolean locked,                       // IN_PROGRESS or later
        boolean modifiable,                   // PLACED/CONFIRMED and nothing paid against it
        boolean late,                         // expected date passed and not yet delivered
        List<OrderItemResponse> items,        // by line number
        List<LinkedWorkResponse> productionBatches,   // batches serving its items
        List<LinkedWorkResponse> outsourcingJobs,     // jobs serving its items
        BigDecimal totalAmount,               // sum of the item amounts
        BigDecimal allocatedAmount,
        BigDecimal outstandingAmount,         // assumption Q2: 0.00 for PLACED and CANCELLED orders
        PaymentStatus paymentStatus,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy) {
}
