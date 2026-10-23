package com.nexora.procurement.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.nexora.finance.application.AllocationQueryService;
import com.nexora.inventory.application.InventoryQueryService;
import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.parties.application.PartyRef;
import com.nexora.parties.application.PartyService;
import com.nexora.procurement.api.PurchaseMapper;
import com.nexora.procurement.api.dto.PurchaseResponse;
import com.nexora.procurement.api.dto.PurchaseSummaryResponse;
import com.nexora.procurement.domain.MaterialReceipt;
import com.nexora.procurement.domain.Purchase;
import com.nexora.procurement.domain.SupplierReturn;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.domain.Money;

// Builds the full purchase view that every purchase use case returns: the aggregate plus the supplier's code and
// name (parties module) and the movement number of each receipt and return (inventory module), each fetched
// with one query, and the allocations of payments (finance module). Runs inside the caller's transaction.
@Component
class PurchaseViews {

    private final PartyService parties;
    private final InventoryQueryService inventory;
    private final AllocationQueryService allocations;
    private final Clock clock;

    PurchaseViews(PartyService parties, InventoryQueryService inventory, AllocationQueryService allocations, Clock clock) {
        this.parties = parties;
        this.inventory = inventory;
        this.allocations = allocations;
        this.clock = clock;
    }

    PurchaseResponse of(Purchase purchase) {
        Map<UUID, String> receiptMovements = inventory.movementNumbersByReference(MovementReferenceType.MATERIAL_RECEIPT,
                purchase.getReceipts().stream().map(MaterialReceipt::getId).toList());
        Map<UUID, String> returnMovements = inventory.movementNumbersByReference(MovementReferenceType.SUPPLIER_RETURN,
                purchase.getReturns().stream().map(SupplierReturn::getId).toList());
        return PurchaseMapper.toResponse(purchase, parties.getRef(purchase.getSupplierId()),
                payment(purchase, allocations.allocatedByPurchase(List.of(purchase.getId()))), receiptMovements,
                returnMovements);
    }

    // List rows: suppliers and allocations of the whole page in one query each.
    PageResponse<PurchaseSummaryResponse> page(Page<Purchase> page) {
        Map<UUID, PartyRef> suppliers = parties.getRefs(page.getContent().stream().map(Purchase::getSupplierId)
                .distinct().toList());
        Map<UUID, BigDecimal> allocated = allocations.allocatedByPurchase(page.getContent().stream()
                .map(Purchase::getId).toList());
        return PageResponse.from(page.map(p -> PurchaseMapper.toSummary(p, suppliers.get(p.getSupplierId()),
                payment(p, allocated))));
    }

    // True when an active payment is allocated to the purchase (it cannot be cancelled then).
    boolean hasAllocations(Purchase purchase) {
        return !allocations.allocatedByPurchase(List.of(purchase.getId())).isEmpty();
    }

    private PurchasePayment payment(Purchase p, Map<UUID, BigDecimal> allocated) {
        return PurchasePayment.of(p.payableAmount(), allocated.getOrDefault(p.getId(), Money.zero()), p.getDueDate(),
                p.getStatus(), LocalDate.now(clock));
    }
}
