package com.nexora.procurement.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.inventory.application.InventoryQueryService;
import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.parties.application.PartyService;
import com.nexora.parties.domain.PartyRole;
import com.nexora.procurement.api.PurchaseMapper;
import com.nexora.procurement.api.dto.CreatePurchaseRequest;
import com.nexora.procurement.api.dto.PurchaseResponse;
import com.nexora.procurement.api.dto.PurchaseSummaryResponse;
import com.nexora.procurement.api.dto.ReceiptResponse;
import com.nexora.procurement.api.dto.SupplierReturnResponse;
import com.nexora.procurement.api.dto.UpdatePurchaseRequest;
import com.nexora.procurement.domain.PaymentType;
import com.nexora.procurement.domain.Purchase;
import com.nexora.procurement.domain.PurchaseStatus;
import com.nexora.procurement.infrastructure.PayableRepository;
import com.nexora.procurement.infrastructure.PurchaseRepository;
import com.nexora.procurement.infrastructure.PurchaseSpecifications;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.domain.PaymentStatus;
import com.nexora.shared.error.FieldValidationException;
import com.nexora.shared.error.ResourceNotFoundException;
import com.nexora.shared.numbering.BusinessNumberGenerator;

// Purchase use cases: create, view, list, edit the free-text fields. Receiving and returning are in
// PurchaseReceivingService because they also move stock.
@Service
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    // Allowed sort properties (E-PROC-02).
    private static final Set<String> SORTABLE = Set.of("purchaseDate", "purchaseNumber", "dueDate", "createdAt");

    private final PurchaseRepository purchases;
    private final PartyService parties;
    private final BusinessNumberGenerator numbers;
    private final PurchaseViews views;
    private final InventoryQueryService inventory;
    private final PayableRepository payables;
    private final Clock clock;

    public PurchaseService(PurchaseRepository purchases, PartyService parties, BusinessNumberGenerator numbers,
                           PurchaseViews views, InventoryQueryService inventory, PayableRepository payables,
                           Clock clock) {
        this.payables = payables;
        this.clock = clock;
        this.purchases = purchases;
        this.parties = parties;
        this.numbers = numbers;
        this.views = views;
        this.inventory = inventory;
    }

    // E-PROC-01. One transaction: a failure after the PUR number was taken rolls the counter back too.
    @Transactional
    public PurchaseResponse create(CreatePurchaseRequest r) {
        // 404 unknown, 422 INVALID_PARTY_ROLE (not a supplier), 422 PARTY_INACTIVE (FR-PARTY-11/12)
        parties.requireActiveWithRole(r.supplierId(), PartyRole.SUPPLIER);
        int creditDays = creditDays(r);
        String number = numbers.next("PUR");                       // last lock taken (ADR-028)
        Purchase purchase = purchases.save(Purchase.create(number, r.supplierId(),
                r.productType() == null ? ProductType.RAW_SILK : r.productType(), r.purchaseDate(),
                r.orderedWeightKg(), r.ratePerKg(), r.paymentType(), creditDays, blankToNull(r.billReference()),
                blankToNull(r.notes())));
        log.info("event=purchase_created number={} kg={}", number, purchase.getOrderedWeightKg());
        return views.of(purchase);
    }

    @Transactional(readOnly = true)
    public PurchaseResponse get(UUID id) {
        return views.of(find(id));
    }

    // E-PROC-08: one receipt; 404 when it does not belong to this purchase.
    @Transactional(readOnly = true)
    public ReceiptResponse getReceipt(UUID purchaseId, UUID receiptId) {
        return find(purchaseId).getReceipts().stream()
                .filter(r -> r.getId().equals(receiptId))
                .findFirst()
                .map(r -> PurchaseMapper.toReceipt(r, inventory.movementNumbersByReference(
                        MovementReferenceType.MATERIAL_RECEIPT, List.of(receiptId)).get(receiptId)))
                .orElseThrow(() -> new ResourceNotFoundException("Receipt", receiptId));
    }

    // E-PROC-09: one return; 404 when it does not belong to this purchase.
    @Transactional(readOnly = true)
    public SupplierReturnResponse getReturn(UUID purchaseId, UUID returnId) {
        return find(purchaseId).getReturns().stream()
                .filter(r -> r.getId().equals(returnId))
                .findFirst()
                .map(r -> PurchaseMapper.toReturn(r, inventory.movementNumbersByReference(
                        MovementReferenceType.SUPPLIER_RETURN, List.of(returnId)).get(returnId)))
                .orElseThrow(() -> new ResourceNotFoundException("Return", returnId));
    }

    // E-PROC-02. Receipts of the page are batch-loaded (@BatchSize) and suppliers fetched in one query.
    @Transactional(readOnly = true)
    public PageResponse<PurchaseSummaryResponse> list(UUID supplierId, PurchaseStatus status, ProductType productType,
                                                      LocalDate from, LocalDate to, PaymentStatus paymentStatus,
                                                      Boolean overdue, Pageable pageable) {
        ListParameters.requireSortable(pageable, SORTABLE);
        ListParameters.requireDateRange(from, to);
        Specification<Purchase> filter = (root, query, cb) -> cb.conjunction();
        for (Specification<Purchase> part : Arrays.asList(PurchaseSpecifications.supplier(supplierId),
                PurchaseSpecifications.status(status), PurchaseSpecifications.product(productType),
                PurchaseSpecifications.from(from), PurchaseSpecifications.to(to),
                paymentFilter(supplierId, paymentStatus, overdue))) {
            if (part != null) {
                filter = filter.and(part);
            }
        }
        return views.page(purchases.findAll(filter, pageable));
    }

    // paymentStatus/overdue are derived, so they cannot be columns in a WHERE clause: the matching ids come from
    // one aggregate query over all (or one supplier's) purchases.
    // ponytail: reads every purchase's payable per filtered request; a SQL-side filter if purchases reach 100k+.
    private Specification<Purchase> paymentFilter(UUID supplierId, PaymentStatus paymentStatus, Boolean overdue) {
        if (paymentStatus == null && overdue == null) {
            return null;
        }
        LocalDate today = LocalDate.now(clock);
        List<UUID> ids = payables.purchases(supplierId).stream()
                .filter(row -> {
                    PurchasePayment pay = PurchasePayment.of(row.payable(), row.allocated(), row.dueDate(),
                            row.status(), today);
                    return (paymentStatus == null || pay.paymentStatus() == paymentStatus)
                            && (overdue == null || pay.overdue() == overdue);
                })
                .map(PayableRepository.PurchaseRow::purchaseId)
                .toList();
        return (root, query, cb) -> ids.isEmpty() ? cb.disjunction() : root.get("id").in(ids);
    }

    // E-PROC-04: only notes and the bill reference; a missing field keeps its value, "" clears it.
    // @Version turns a concurrent change into 409 CONCURRENT_MODIFICATION.
    @Transactional
    public PurchaseResponse update(UUID id, UpdatePurchaseRequest r) {
        Purchase purchase = find(id);
        purchase.updateNotes(r.notes() == null ? purchase.getNotes() : blankToNull(r.notes()),
                r.billReference() == null ? purchase.getBillReference() : blankToNull(r.billReference()));
        return views.of(purchases.saveAndFlush(purchase));         // flush: the response shows the new updatedAt
    }

    // CASH is due at once (0 days, FR-PROC-04 AC2); CREDIT takes the request, else the supplier's default, else 0.
    private int creditDays(CreatePurchaseRequest r) {
        if (r.paymentType() == PaymentType.CASH) {
            if (r.creditDays() != null && r.creditDays() != 0) {
                throw new FieldValidationException("creditDays", "A cash purchase has no credit days.");
            }
            return 0;
        }
        if (r.creditDays() != null) {
            return r.creditDays();
        }
        Integer supplierDefault = parties.defaultCreditDays(r.supplierId());
        return supplierDefault == null ? 0 : supplierDefault;
    }

    Purchase find(UUID id) {
        return purchases.findById(id).orElseThrow(() -> new ResourceNotFoundException("Purchase", id));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
