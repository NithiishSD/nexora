package com.nexora.orders.application;

import java.util.UUID;

import com.nexora.inventory.domain.ProductType;
import com.nexora.orders.domain.OrderStatus;

// What production and outsourcing may know about an order item (03-ARCHITECTURE 5.5): enough to validate a link
// and to show "ORD-2026-0003 line 2". They never receive the entity.
public record OrderItemRef(UUID itemId, UUID orderId, String orderNumber, int lineNo, ProductType productType,
                           OrderStatus orderStatus) {
}
