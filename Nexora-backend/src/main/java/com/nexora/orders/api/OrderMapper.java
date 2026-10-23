package com.nexora.orders.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nexora.orders.api.dto.LinkedWorkResponse;
import com.nexora.orders.api.dto.OrderItemResponse;
import com.nexora.orders.api.dto.OrderResponse;
import com.nexora.orders.api.dto.OrderSummaryResponse;
import com.nexora.orders.domain.CustomerOrder;
import com.nexora.orders.domain.OrderItem;
import com.nexora.orders.domain.OrderItemStatus;
import com.nexora.parties.application.PartyRef;
import com.nexora.shared.domain.Money;
import com.nexora.shared.domain.PaymentStatus;

// Turns the order aggregate into API records, inside the service transaction (items are lazy). The customer
// reference and the allocated amount per item come from other modules and are passed in.
public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderResponse toResponse(CustomerOrder o, PartyRef customer, Map<UUID, BigDecimal> allocatedByItem,
                                           List<LinkedWorkResponse> productionBatches,
                                           List<LinkedWorkResponse> outsourcingJobs, LocalDate today) {
        List<OrderItemResponse> items = o.getItems().stream().map(i -> toItem(o, i, allocatedByItem)).toList();
        BigDecimal allocated = sum(items.stream().map(OrderItemResponse::allocatedAmount).toList());
        BigDecimal total = o.totalAmount();
        boolean hasAllocations = allocated.signum() > 0;
        return new OrderResponse(o.getId(), o.getOrderNumber(), customer, o.getOrderDate(), o.getRequiredDate(),
                o.getDeliveredOn(), o.getStatus(), o.isLocked(), o.canModify(hasAllocations), o.isLate(today), items,
                productionBatches, outsourcingJobs,
                total, allocated, outstanding(o, total, allocated), PaymentStatus.of(allocated, total.subtract(allocated)),
                o.getNotes(), o.getCreatedAt(), o.getUpdatedAt(), o.getCreatedBy());
    }

    public static OrderSummaryResponse toSummary(CustomerOrder o, PartyRef customer,
                                                 Map<UUID, BigDecimal> allocatedByItem, LocalDate today) {
        BigDecimal allocated = sum(o.getItems().stream().map(i -> allocatedOf(i, allocatedByItem)).toList());
        BigDecimal total = o.totalAmount();
        return new OrderSummaryResponse(o.getId(), o.getOrderNumber(), customer, o.getOrderDate(), o.getRequiredDate(),
                o.getStatus(), total, outstanding(o, total, allocated),
                PaymentStatus.of(allocated, total.subtract(allocated)), o.isLate(today), o.getItems().size());
    }

    private static OrderItemResponse toItem(CustomerOrder o, OrderItem i, Map<UUID, BigDecimal> allocatedByItem) {
        BigDecimal allocated = allocatedOf(i, allocatedByItem);
        // ASSUMPTION Q2: a line is owed only while its order is an obligation and the line is not cancelled
        boolean owed = o.getStatus().isObligation() && i.getStatus() != OrderItemStatus.CANCELLED;
        BigDecimal outstanding = owed ? i.getAmount().subtract(allocated) : Money.zero();
        return new OrderItemResponse(i.getId(), i.getLineNo(), i.getProductType(), i.getRequiredWeightKg(),
                i.getRatePerKg(), i.getAmount(), i.getWarpSpecification(), i.getStatus(), allocated, outstanding,
                PaymentStatus.of(allocated, i.getAmount().subtract(allocated)), i.getNotes());
    }

    // ASSUMPTION Q2: PLACED and CANCELLED orders owe nothing (05-API-SPEC section 22 decision 22).
    private static BigDecimal outstanding(CustomerOrder o, BigDecimal total, BigDecimal allocated) {
        return o.getStatus().isObligation() ? total.subtract(allocated) : Money.zero();
    }

    private static BigDecimal allocatedOf(OrderItem item, Map<UUID, BigDecimal> allocatedByItem) {
        return allocatedByItem.getOrDefault(item.getId(), Money.zero());
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(Money.zero(), BigDecimal::add);
    }
}
