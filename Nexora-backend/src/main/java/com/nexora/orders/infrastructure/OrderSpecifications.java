package com.nexora.orders.infrastructure;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.nexora.inventory.domain.ProductType;
import com.nexora.orders.domain.CustomerOrder;
import com.nexora.orders.domain.OrderItem;
import com.nexora.orders.domain.OrderStatus;

import jakarta.persistence.criteria.Subquery;

// Filters for the order list (E-ORD-02). Each returns null when unused; values are bound parameters.
public final class OrderSpecifications {

    // FR-ORD-05: "pending" = not yet delivered and not cancelled
    private static final List<OrderStatus> PENDING =
            Arrays.stream(OrderStatus.values()).filter(OrderStatus::isPending).toList();

    private OrderSpecifications() {
    }

    public static Specification<CustomerOrder> customer(UUID customerId) {
        return customerId == null ? null : (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<CustomerOrder> status(OrderStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    // pending=true: only the four open statuses; pending=false: delivered, completed or cancelled.
    public static Specification<CustomerOrder> pending(Boolean pending) {
        if (pending == null) {
            return null;
        }
        return (root, query, cb) -> pending ? root.get("status").in(PENDING) : cb.not(root.get("status").in(PENDING));
    }

    // late=true: expected date before today and still pending (FR-ORD-16); late=false: the rest.
    public static Specification<CustomerOrder> late(Boolean late, LocalDate today) {
        if (late == null) {
            return null;
        }
        return (root, query, cb) -> {
            // isNotNull first: without an expected date the order is never late (and NOT(NULL) would drop it)
            var isLate = cb.and(cb.isNotNull(root.get("requiredDate")), cb.lessThan(root.get("requiredDate"), today),
                    root.get("status").in(PENDING));
            return late ? isLate : cb.not(isLate);
        };
    }

    // Orders that contain at least one item of the product (EXISTS, so an order appears once).
    public static Specification<CustomerOrder> product(ProductType productType) {
        if (productType == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<UUID> items = query.subquery(UUID.class);
            var item = items.from(OrderItem.class);
            items.select(item.get("id")).where(cb.equal(item.get("order"), root),
                    cb.equal(item.get("productType"), productType));
            return cb.exists(items);
        };
    }

    // Inclusive order-date range.
    public static Specification<CustomerOrder> from(LocalDate from) {
        return from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("orderDate"), from);
    }

    public static Specification<CustomerOrder> to(LocalDate to) {
        return to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("orderDate"), to);
    }
}
