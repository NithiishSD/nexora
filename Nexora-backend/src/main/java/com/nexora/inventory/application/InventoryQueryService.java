package com.nexora.inventory.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.inventory.api.dto.ExternalWipResponse;
import com.nexora.inventory.api.dto.MovementResponse;
import com.nexora.inventory.api.dto.StockSummaryResponse;
import com.nexora.inventory.domain.InventoryBalance;
import com.nexora.inventory.domain.InventoryThreshold;
import com.nexora.inventory.domain.MaterialMovement;
import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.inventory.infrastructure.ExternalWipRepository;
import com.nexora.inventory.infrastructure.InventoryBalanceRepository;
import com.nexora.inventory.infrastructure.InventoryThresholdRepository;
import com.nexora.inventory.infrastructure.MaterialMovementRepository;
import com.nexora.inventory.infrastructure.MovementSpecifications;
import com.nexora.inventory.infrastructure.ProductQuantity;
import com.nexora.parties.application.PartyRef;
import com.nexora.parties.application.PartyService;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.domain.Weight;
import com.nexora.shared.error.ResourceNotFoundException;

// The read side of inventory: no locks, read-only transactions. Other modules use it too
// (procurement shows the movement number of each receipt).
@Service
@Transactional(readOnly = true)
public class InventoryQueryService {

    private static final Set<String> SORTABLE = Set.of("movementDate", "movementNumber", "createdAt");

    // MOV-2026-0012 -> written after MOV-2026-0011 and before MOV-2027-0001 (the counter restarts each year)
    private static final Comparator<MaterialMovement> WRITE_ORDER = Comparator
            .comparing((MaterialMovement m) -> m.getMovementNumber().substring(4, 8))
            .thenComparingInt(m -> Integer.parseInt(m.getMovementNumber().substring(9)));

    private final InventoryBalanceRepository balances;
    private final InventoryThresholdRepository thresholds;
    private final MaterialMovementRepository movements;
    private final ExternalWipRepository externalWip;
    private final PartyService parties;
    private final Clock clock;

    public InventoryQueryService(InventoryBalanceRepository balances, InventoryThresholdRepository thresholds,
                                 MaterialMovementRepository movements, ExternalWipRepository externalWip,
                                 PartyService parties, Clock clock) {
        this.balances = balances;
        this.thresholds = thresholds;
        this.movements = movements;
        this.externalWip = externalWip;
        this.parties = parties;
        this.clock = clock;
    }

    // E-INV-01. The balances are a handful of rows (one per product and location), so they are read whole;
    // wastage is summed by the database.
    public StockSummaryResponse summary() {
        Map<String, BigDecimal> stored = balances.findAll().stream()
                .collect(Collectors.toMap(b -> key(b.getProductType(), b.getLocation()), InventoryBalance::getQuantityKg));
        Map<String, BigDecimal> thresholdMap = thresholds.findAll().stream()
                .collect(Collectors.toMap(t -> key(t.getProductType(), t.getLocation()), InventoryThreshold::getMinThresholdKg));
        // every legal (product, internal location) pair, 0.000 when nothing was ever stored there (FR-INV-07 AC3)
        List<StockSummaryResponse.Balance> lines = Arrays.stream(StockLocation.values())
                .filter(StockLocation::isInternal)
                .flatMap(location -> Arrays.stream(ProductType.values())
                        .filter(location::holds)
                        .map(product -> {
                            BigDecimal qty = Weight.round(stored.getOrDefault(key(product, location), BigDecimal.ZERO));
                            BigDecimal min = thresholdMap.get(key(product, location));
                            boolean lowStock = min != null && min.compareTo(BigDecimal.ZERO) > 0 && qty.compareTo(min) <= 0;
                            return new StockSummaryResponse.Balance(product, location, qty, min, lowStock);
                        }))
                .toList();

        Map<ProductType, BigDecimal> wastage = new EnumMap<>(ProductType.class);
        for (ProductQuantity row : movements.wastageTotals()) {
            wastage.put(row.productType(), row.quantityKg());
        }
        return new StockSummaryResponse(lines,
                sum(lines, l -> l.productType() == ProductType.RAW_SILK && l.location() == StockLocation.RAW_STOCK),
                sum(lines, l -> l.location() == StockLocation.INTERNAL_WIP),
                sum(lines, l -> l.location() == StockLocation.EXTERNAL_WIP),
                sum(lines, l -> l.productType() == ProductType.SILK_WARP && l.location() == StockLocation.FINISHED_STOCK),
                sum(lines, l -> l.productType() == ProductType.KORA_WARP && l.location() == StockLocation.FINISHED_STOCK),
                sum(lines, l -> l.productType() == ProductType.VUDA_WARP && l.location() == StockLocation.RAW_STOCK),
                Arrays.stream(ProductType.values())
                        .map(p -> new StockSummaryResponse.Wastage(p, Weight.round(wastage.getOrDefault(p, BigDecimal.ZERO))))
                        .toList(),
                "kg",
                Instant.now(clock));
    }

    // E-INV-02: filtered, paged movement history, newest first by default.
    public PageResponse<MovementResponse> movements(ProductType product, StockLocation location, MovementType type,
                                                    MovementReferenceType referenceType, UUID referenceId,
                                                    LocalDate from, LocalDate to, Pageable pageable) {
        ListParameters.requireSortable(pageable, SORTABLE);
        ListParameters.requireDateRange(from, to);
        Specification<MaterialMovement> filter = (root, query, cb) -> cb.conjunction();
        for (Specification<MaterialMovement> part : Arrays.asList(MovementSpecifications.product(product),
                MovementSpecifications.location(location), MovementSpecifications.type(type),
                MovementSpecifications.referenceType(referenceType), MovementSpecifications.referenceId(referenceId),
                MovementSpecifications.from(from), MovementSpecifications.to(to))) {
            if (part != null) {
                filter = filter.and(part);
            }
        }
        return PageResponse.from(movements.findAll(filter, pageable).map(InventoryQueryService::toResponse));
    }

    // E-INV-03
    public MovementResponse movement(UUID id) {
        return movements.findById(id).map(InventoryQueryService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Movement", id));
    }

    // For other modules: the movement number of each source record (e.g. receipt id -> MOV-2026-0001), one query.
    // Records without a movement (a receipt with 0 kg accepted) are simply absent from the map.
    public Map<UUID, String> movementNumbersByReference(MovementReferenceType referenceType, Collection<UUID> referenceIds) {
        if (referenceIds.isEmpty()) {
            return Map.of();
        }
        return movements.findByReferenceTypeAndReferenceIdIn(referenceType, referenceIds).stream()
                .collect(Collectors.toMap(MaterialMovement::getReferenceId, MaterialMovement::getMovementNumber));
    }

    // The number of one kind of movement per record, when a record has several (a batch: CONSUMPTION on start,
    // PRODUCTION_OUTPUT and WASTAGE on completion).
    @Transactional(readOnly = true)
    public Map<UUID, String> movementNumbersByReference(MovementReferenceType referenceType, MovementType movementType,
                                                        Collection<UUID> referenceIds) {
        if (referenceIds.isEmpty()) {
            return Map.of();
        }
        return movements.findByReferenceTypeAndReferenceIdIn(referenceType, referenceIds).stream()
                .filter(m -> m.getMovementType() == movementType)
                .collect(Collectors.toMap(MaterialMovement::getReferenceId, MaterialMovement::getMovementNumber));
    }

    // For other modules: every movement of one record in the order it was written. Movements written by one call
    // (e.g. a job receipt and its wastage) are next to each other: the MOV counter row stays locked until commit.
    public List<MovementResponse> movementsOf(MovementReferenceType referenceType, UUID referenceId) {
        return movements.findByReferenceTypeAndReferenceIdIn(referenceType, List.of(referenceId)).stream()
                .sorted(WRITE_ORDER).map(InventoryQueryService::toResponse).toList();
    }

    // E-INV-04. Without a manufacturer the total is the EXTERNAL_WIP balance itself (FR-INV-14 AC2: the open jobs add
    // up to it); for one manufacturer (404 if unknown) it is that manufacturer's share.
    public ExternalWipResponse externalWip(UUID manufacturerId) {
        if (manufacturerId != null) {
            parties.getRef(manufacturerId);
        }
        Map<UUID, List<ExternalWipRepository.OpenJob>> byManufacturer = externalWip.openJobs(manufacturerId).stream()
                .collect(Collectors.groupingBy(ExternalWipRepository.OpenJob::manufacturerId, LinkedHashMap::new,
                        Collectors.toList()));
        Map<UUID, PartyRef> refs = parties.getRefs(byManufacturer.keySet());
        List<ExternalWipResponse.Manufacturer> rows = byManufacturer.entrySet().stream().map(e -> {
            List<ExternalWipResponse.Job> jobs = e.getValue().stream().map(j -> new ExternalWipResponse.Job(j.id(),
                    j.jobNumber(), j.productType(), j.status(), j.issueDate(), j.issuedKg(), j.receivedKg(),
                    j.wastageKg(), Weight.round(j.issuedKg().subtract(j.receivedKg()).subtract(j.wastageKg()))))
                    .toList();
            return new ExternalWipResponse.Manufacturer(refs.get(e.getKey()),
                    total(jobs, ExternalWipResponse.Job::issuedKg), total(jobs, ExternalWipResponse.Job::receivedKg),
                    total(jobs, ExternalWipResponse.Job::wastageKg), total(jobs, ExternalWipResponse.Job::outsideKg), jobs);
        }).sorted(Comparator.comparing(m -> m.manufacturer().name())).toList();
        BigDecimal total = manufacturerId != null ? total(rows, ExternalWipResponse.Manufacturer::outsideKg)
                : total(balances.findAll().stream().filter(b -> b.getLocation() == StockLocation.EXTERNAL_WIP).toList(),
                        InventoryBalance::getQuantityKg);
        return new ExternalWipResponse(total, rows);
    }

    private static <T> BigDecimal total(List<T> rows, Function<T, BigDecimal> kg) {
        return Weight.round(rows.stream().map(kg).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    static MovementResponse toResponse(MaterialMovement m) {
        return new MovementResponse(m.getId(), m.getMovementNumber(), m.getMovementType(), m.getProductType(),
                m.getToProductType(), m.getQuantityKg(), m.getFromLocation(), m.getToLocation(), m.getMovementDate(),
                m.getReferenceType(), m.getReferenceId(), m.getReferenceNumber(), m.getReason(), m.getNotes(),
                m.getCreatedAt(), m.getCreatedBy());
    }

    private static BigDecimal sum(List<StockSummaryResponse.Balance> lines,
                                  Predicate<StockSummaryResponse.Balance> include) {
        return Weight.round(lines.stream().filter(include)
                .map(StockSummaryResponse.Balance::quantityKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public BigDecimal getDeliveredWeight(MovementReferenceType referenceType, UUID referenceId) {
        return Weight.round(movements.sumDeliveredWeight(referenceType, referenceId));
    }

    private static String key(ProductType product, StockLocation location) {
        return product + "/" + location;
    }
}
