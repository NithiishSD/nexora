package com.nexora.inventory.application;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.inventory.domain.InventoryBalance;
import com.nexora.inventory.domain.MaterialMovement;
import com.nexora.inventory.domain.MovementRules;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.inventory.infrastructure.InventoryBalanceRepository;
import com.nexora.inventory.infrastructure.MaterialMovementRepository;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.numbering.BusinessNumberGenerator;

// The ONLY writer of stock (DOMAIN_RULES rule 5, 03-ARCHITECTURE 5.3). Procurement, orders, production and
// outsourcing call recordMovement from inside their own use case; nothing else touches material_movements
// or inventory_balances, and no HTTP route accepts a movement from a client.
@Service
public class InventoryService {

    // Global order for balance locks (ADR-028): ascending product type, then location. Two movements that
    // touch the same two rows lock them in the same order, so they wait for each other instead of deadlocking.
    private static final Comparator<Key> LOCK_ORDER =
            Comparator.comparing((Key k) -> k.product().name()).thenComparing(k -> k.location().name());

    private final InventoryBalanceRepository balances;
    private final MaterialMovementRepository movements;
    private final BusinessNumberGenerator numbers;

    public InventoryService(InventoryBalanceRepository balances, MaterialMovementRepository movements,
                            BusinessNumberGenerator numbers) {
        this.balances = balances;
        this.movements = movements;
        this.numbers = numbers;
    }

    // MANDATORY: must run inside the caller's use-case transaction (a receipt and its movement commit or roll
    // back together, NFR-REL-02). Called without one, Spring throws IllegalTransactionStateException.
    @Transactional(propagation = Propagation.MANDATORY)
    public MovementResult recordMovement(MovementCommand c) {
        return recordMovements(List.of(c)).getFirst();
    }

    // All movements of ONE use case (every item of a delivery, output plus wastage of a batch): all or none.
    // Every balance row is locked before any MOV number is taken. Locking movement by movement would hold the
    // MOV counter row while waiting for the next balance row, which deadlocks against any movement that holds
    // that balance row and then asks for a number.
    @Transactional(propagation = Propagation.MANDATORY)
    public List<MovementResult> recordMovements(List<MovementCommand> commands) {
        // 1. Pure rules first: no lock is taken for a request that can never succeed.
        commands.forEach(c -> MovementRules.validate(c.movementType(), c.productType(), c.toProductType(),
                c.fromLocation(), c.toLocation(), c.quantityKg(), c.reason()));

        // 2. Lock every internal balance row the movements touch, once each, in the global order.
        Map<Key, InventoryBalance> rows = new HashMap<>();
        commands.stream()
                .flatMap(c -> Stream.of(source(c), destination(c)))
                .filter(Objects::nonNull)
                .distinct()
                .sorted(LOCK_ORDER)
                .forEach(key -> rows.put(key, lock(key)));

        for (MovementCommand c : commands) {
            // 3. Opening stock exists once per product and location (FR-INV-13); checked while the row is locked,
            //    so two parallel opening entries cannot both pass (the partial unique index is the backstop).
            if (c.movementType() == MovementType.OPENING_BALANCE
                    && movements.existsByMovementTypeAndToProductTypeAndToLocation(
                            MovementType.OPENING_BALANCE, c.toProductType(), c.toLocation())) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Opening stock of " + c.toProductType()
                        + " in " + c.toLocation() + " was already entered; use an adjustment to correct it.");
            }
            // 4. Check and change the balances. remove() throws INSUFFICIENT_INVENTORY (409) when stock is too
            //    low; the transaction then rolls back and nothing is stored, also for earlier movements.
            Key source = source(c);
            if (source != null) {
                rows.get(source).remove(c.quantityKg());
            }
            Key destination = destination(c);
            if (destination != null) {
                rows.get(destination).add(c.quantityKg());
            }
        }

        // 5. Numbers last (ADR-028: the hot counter row is held for the shortest time), then the ledger rows.
        //    The changed balances need no save(): they are managed entities, written on commit (dirty checking).
        return commands.stream().map(c -> {
            String number = numbers.next("MOV");
            MaterialMovement movement = movements.save(MaterialMovement.record(number, c.movementType(),
                    c.productType(), c.toProductType(), c.fromLocation(), c.toLocation(), c.quantityKg(),
                    c.movementDate(), c.referenceType(), c.referenceId(), c.referenceNumber(), c.reason(), c.notes()));
            return new MovementResult(movement.getId(), number);
        }).toList();
    }

    // The source row is keyed by the product leaving, the destination row by the product arriving; external
    // locations (supplier, customer, wastage, returned) have no balance row.
    private static Key source(MovementCommand c) {
        return c.fromLocation() != null && c.fromLocation().isInternal() ? new Key(c.productType(), c.fromLocation()) : null;
    }

    private static Key destination(MovementCommand c) {
        return c.toLocation().isInternal() ? new Key(c.toProductType(), c.toLocation()) : null;
    }

    // Ensure the row exists (0 kg the first time), then SELECT ... FOR UPDATE it.
    private InventoryBalance lock(Key key) {
        balances.insertIfAbsent(key.product().name(), key.location().name());
        return balances.findForUpdate(key.product(), key.location())
                .orElseThrow(() -> new IllegalStateException("Balance row missing for " + key));
    }

    // One balance row: (product, internal location).
    private record Key(ProductType product, StockLocation location) {
    }
}
