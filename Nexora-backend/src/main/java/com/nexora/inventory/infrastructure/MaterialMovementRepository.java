package com.nexora.inventory.infrastructure;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.nexora.inventory.domain.MaterialMovement;
import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;

// The ledger is append-only: the application only ever calls save() with a new movement and reads.
public interface MaterialMovementRepository extends JpaRepository<MaterialMovement, UUID>,
        JpaSpecificationExecutor<MaterialMovement> {

    // Derived query (Spring Data builds the SQL from the method name): is there already such a movement?
    boolean existsByMovementTypeAndToProductTypeAndToLocation(MovementType movementType, ProductType toProductType,
                                                              StockLocation toLocation);

    // All movements caused by these records (e.g. the RECEIPT movements of a purchase's receipts), one query.
    List<MaterialMovement> findByReferenceTypeAndReferenceIdIn(MovementReferenceType referenceType,
                                                               Collection<UUID> referenceIds);

    // Cumulative wastage per product, summed by the database (GROUP BY), never by looping in Java.
    @Query("""
            select new com.nexora.inventory.infrastructure.ProductQuantity(
                m.productType, sum(m.quantityKg))
            from MaterialMovement m
            where m.toLocation = com.nexora.inventory.domain.StockLocation.WASTAGE
            group by m.productType
            """)
    List<ProductQuantity> wastageTotals();

    @Query("""
            select coalesce(sum(m.quantityKg), 0)
            from MaterialMovement m
            where m.referenceType = :referenceType and m.referenceId = :referenceId
              and m.movementType in (com.nexora.inventory.domain.MovementType.DELIVERY, com.nexora.inventory.domain.MovementType.DIRECT_SALE)
            """)
    BigDecimal sumDeliveredWeight(MovementReferenceType referenceType, UUID referenceId);
}
