package com.nexora.inventory.infrastructure;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.nexora.inventory.domain.MaterialMovement;
import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;

// Filters for the movement history (E-INV-02). Each returns null when unused, and the service ANDs the rest.
// Criteria API: values are always bound parameters, never SQL text.
public final class MovementSpecifications {

    private MovementSpecifications() {
    }

    // A product matches on either side: raw silk consumed into kora WIP shows up for RAW_SILK and for KORA_WARP.
    public static Specification<MaterialMovement> product(ProductType product) {
        return product == null ? null : (root, query, cb) -> cb.or(
                cb.equal(root.get("productType"), product), cb.equal(root.get("toProductType"), product));
    }

    // A location matches on either side (stock came in or went out there).
    public static Specification<MaterialMovement> location(StockLocation location) {
        return location == null ? null : (root, query, cb) -> cb.or(
                cb.equal(root.get("fromLocation"), location), cb.equal(root.get("toLocation"), location));
    }

    public static Specification<MaterialMovement> type(MovementType type) {
        return type == null ? null : (root, query, cb) -> cb.equal(root.get("movementType"), type);
    }

    public static Specification<MaterialMovement> referenceType(MovementReferenceType type) {
        return type == null ? null : (root, query, cb) -> cb.equal(root.get("referenceType"), type);
    }

    public static Specification<MaterialMovement> referenceId(UUID id) {
        return id == null ? null : (root, query, cb) -> cb.equal(root.get("referenceId"), id);
    }

    // Inclusive business-date range.
    public static Specification<MaterialMovement> from(LocalDate from) {
        return from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("movementDate"), from);
    }

    public static Specification<MaterialMovement> to(LocalDate to) {
        return to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("movementDate"), to);
    }
}
