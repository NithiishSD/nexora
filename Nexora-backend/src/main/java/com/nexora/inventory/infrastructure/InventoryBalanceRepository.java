package com.nexora.inventory.infrastructure;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.inventory.domain.InventoryBalance;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;

import jakarta.persistence.LockModeType;

// Only InventoryService uses this repository (ModuleBoundaryTest keeps other modules out).
public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, UUID> {

    Optional<InventoryBalance> findByProductTypeAndLocation(ProductType productType, StockLocation location);

    // Creates the balance row with 0 kg the first time a (product, location) is used. If it already exists,
    // or another transaction is creating it right now, ON CONFLICT DO NOTHING makes this a no-op instead of
    // a unique-constraint error. Native SQL because ON CONFLICT is PostgreSQL syntax, not JPQL.
    @Modifying
    @Query(value = """
            insert into inventory_balances (product_type, location)
            values (:productType, :location)
            on conflict (product_type, location) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("productType") String productType, @Param("location") String location);

    // SELECT ... FOR UPDATE: the row stays locked until our transaction ends, so a second request for the
    // same stock WAITS here and then sees the already reduced quantity. This lock is what makes
    // "check stock, then reduce it" safe under concurrency (FR-INV-05). Waiting is bounded by lock_timeout (5 s).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBalance b where b.productType = :productType and b.location = :location")
    Optional<InventoryBalance> findForUpdate(@Param("productType") ProductType productType,
                                             @Param("location") StockLocation location);

    // Rebuilds every balance from the ledger (in = to_product_type at to_location, out = product_type at
    // from_location) and returns only the (product, location) pairs where the stored balance differs.
    // FULL OUTER JOIN: also catches a balance row without movements and movements without a balance row.
    @Query(value = """
            with flows as (
                select to_product_type as product_type, to_location as location, quantity_kg as qty
                from material_movements
                where to_location in ('RAW_STOCK', 'INTERNAL_WIP', 'EXTERNAL_WIP', 'FINISHED_STOCK')
                union all
                select product_type, from_location, -quantity_kg
                from material_movements
                where from_location in ('RAW_STOCK', 'INTERNAL_WIP', 'EXTERNAL_WIP', 'FINISHED_STOCK')
            ), ledger as (
                select product_type, location, sum(qty) as total from flows group by product_type, location
            )
            select coalesce(b.product_type, l.product_type) as productType,
                   coalesce(b.location, l.location) as location,
                   coalesce(l.total, 0) as expectedKg,
                   coalesce(b.quantity_kg, 0) as actualKg
            from inventory_balances b
            full outer join ledger l on l.product_type = b.product_type and l.location = b.location
            where coalesce(l.total, 0) <> coalesce(b.quantity_kg, 0)
            order by 1, 2
            """, nativeQuery = true)
    List<BalanceDifference> findDifferencesFromLedger();

    // One row of the reconciliation query (Spring Data maps the column aliases to these getters).
    interface BalanceDifference {
        String getProductType();

        String getLocation();

        BigDecimal getExpectedKg();

        BigDecimal getActualKg();
    }
}
