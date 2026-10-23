package com.nexora.procurement.infrastructure;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.nexora.inventory.domain.ProductType;
import com.nexora.procurement.domain.Purchase;
import com.nexora.procurement.domain.PurchaseStatus;

// Filters for the purchase list (E-PROC-02). Each returns null when unused; values are bound parameters.
public final class PurchaseSpecifications {

    private PurchaseSpecifications() {
    }

    public static Specification<Purchase> supplier(UUID supplierId) {
        return supplierId == null ? null : (root, query, cb) -> cb.equal(root.get("supplierId"), supplierId);
    }

    public static Specification<Purchase> status(PurchaseStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Purchase> product(ProductType productType) {
        return productType == null ? null : (root, query, cb) -> cb.equal(root.get("productType"), productType);
    }

    // Inclusive purchase-date range.
    public static Specification<Purchase> from(LocalDate from) {
        return from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("purchaseDate"), from);
    }

    public static Specification<Purchase> to(LocalDate to) {
        return to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("purchaseDate"), to);
    }
}
