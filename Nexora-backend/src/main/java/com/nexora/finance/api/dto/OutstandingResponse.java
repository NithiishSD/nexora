package com.nexora.finance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexora.orders.domain.OrderStatus;
import com.nexora.parties.application.PartyRef;

// GET /api/v1/customers/{id}/outstanding (E-PAY-06). Only orders CONFIRMED..COMPLETED are owed (assumption Q2).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutstandingResponse(
        PartyRef customer,
        Integer creditDays,
        BigDecimal totalOutstanding,
        BigDecimal advance,                         // unallocated part of active payments, not revenue
        LocalDate asOf,
        Ageing ageing,                              // by order (or obligation) date
        List<OrderLine> orders,                     // owed orders with something outstanding, oldest first
        List<OpeningLine> openingObligations) {

    public record Ageing(BigDecimal days0to30, BigDecimal days31to60, BigDecimal days61to90, BigDecimal over90) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrderLine(UUID orderId, String orderNumber, LocalDate orderDate, OrderStatus status, BigDecimal amount,
                            BigDecimal allocated, BigDecimal outstanding, int ageDays,
                            LocalDate dueDate, Boolean overdue) {

        public OrderLine(UUID orderId, String orderNumber, LocalDate orderDate, OrderStatus status, BigDecimal amount,
                         BigDecimal allocated, BigDecimal outstanding, int ageDays) {
            this(orderId, orderNumber, orderDate, status, amount, allocated, outstanding, ageDays, null, null);
        }
    }

    public record OpeningLine(UUID id, LocalDate obligationDate, String referenceNote, BigDecimal originalAmount,
                              BigDecimal allocated, BigDecimal outstanding, int ageDays) {
    }
}
