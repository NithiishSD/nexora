package com.nexora.procurement.application;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.inventory.application.InventoryService;
import com.nexora.inventory.application.MovementCommand;
import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.procurement.api.dto.PurchaseResponse;
import com.nexora.procurement.api.dto.RecordReceiptRequest;
import com.nexora.procurement.api.dto.RecordReturnRequest;
import com.nexora.procurement.domain.MaterialReceipt;
import com.nexora.procurement.domain.Purchase;
import com.nexora.shared.audit.AuditAction;
import com.nexora.shared.audit.AuditService;
import com.nexora.procurement.domain.SupplierReturn;
import com.nexora.procurement.infrastructure.PurchaseRepository;
import com.nexora.shared.domain.Weight;
import com.nexora.shared.error.ResourceNotFoundException;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.numbering.BusinessNumberGenerator;

// The procurement use cases that move stock. Each is ONE transaction: the receipt (or return), the purchase
// status and the stock movement are stored together or not at all (FR-PROC-10 AC3, NFR-REL-02).
//
// Lock order (ADR-028, with one documented step): purchase row -> document number (RCT/RTN) -> inventory
// balance rows -> MOV number. The document number comes before the balances because the movement must point
// at the saved receipt, and the receipt needs its number to be saved. No cycle is possible: RCT and RTN are
// taken only here, always after the purchase lock and before any balance lock.
@Service
public class PurchaseReceivingService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseReceivingService.class);

    private final PurchaseRepository purchases;
    private final InventoryService inventory;
    private final BusinessNumberGenerator numbers;
    private final PurchaseViews views;
    private final AuditService audit;

    public PurchaseReceivingService(PurchaseRepository purchases, InventoryService inventory,
                                    BusinessNumberGenerator numbers, PurchaseViews views, AuditService audit) {
        this.audit = audit;
        this.purchases = purchases;
        this.inventory = inventory;
        this.numbers = numbers;
        this.views = views;
    }

    // E-PROC-06: record what arrived; only the accepted kg enter RAW_STOCK.
    @Transactional
    public RecordedDocument receive(UUID purchaseId, RecordReceiptRequest r) {
        // 1. Lock the purchase: two receipts of one purchase run one after the other, so the
        //    "total received <= ordered" check below cannot be raced.
        Purchase purchase = lockPurchase(purchaseId);
        // 2. The aggregate checks its rules and moves its status (409 / 400 on failure; everything rolls back).
        String number = numbers.next("RCT");
        MaterialReceipt receipt = purchase.recordReceipt(number, r.receiptDate(), r.receivedWeightKg(),
                r.acceptedWeightKg(), r.rejectedWeightKg(), blankToNull(r.notes()));
        // 3. Insert the receipt now, so it has an id the movement can point at. flush() (not save()): the purchase
        //    is already managed, and flushing cascades PERSIST to the new receipt. save() would call merge(),
        //    which copies a new child only when MERGE cascades too.
        purchases.flush();
        // 4. Stock: a RECEIPT movement SUPPLIER -> RAW_STOCK for the accepted kg only; none when 0 kg accepted.
        if (Weight.isPositive(receipt.getAcceptedWeightKg())) {
            inventory.recordMovement(new MovementCommand(purchase.getProductType(), null, MovementType.RECEIPT,
                    StockLocation.SUPPLIER, StockLocation.RAW_STOCK, receipt.getAcceptedWeightKg(),
                    receipt.getReceiptDate(), MovementReferenceType.MATERIAL_RECEIPT, receipt.getId(), number,
                    null, null));
        }
        log.info("event=purchase_receipt_recorded number={} purchase={} acceptedKg={}", number,
                purchase.getPurchaseNumber(), receipt.getAcceptedWeightKg());
        return new RecordedDocument(receipt.getId(), views.of(purchase));
    }

    // E-PROC-07: send material back. The purchase limits it to accepted - returned (409 INSUFFICIENT_INVENTORY),
    // the inventory to what is still in RAW_STOCK (the rest may have been consumed; same code).
    @Transactional
    public RecordedDocument recordReturn(UUID purchaseId, RecordReturnRequest r) {
        Purchase purchase = lockPurchase(purchaseId);
        String number = numbers.next("RTN");
        SupplierReturn supplierReturn = purchase.recordReturn(number, r.returnDate(), r.returnedWeightKg(), r.reason(),
                blankToNull(r.notes()));
        purchases.flush();                                          // the return gets its id (see receive)
        // the receipt is never rewritten (FR-PROC-14): the return is its own record plus this movement
        inventory.recordMovement(new MovementCommand(purchase.getProductType(), null, MovementType.PURCHASE_RETURN,
                StockLocation.RAW_STOCK, StockLocation.RETURNED, supplierReturn.getReturnedWeightKg(),
                supplierReturn.getReturnDate(), MovementReferenceType.SUPPLIER_RETURN, supplierReturn.getId(), number,
                null, null));
        log.info("event=supplier_return_recorded number={} purchase={} kg={}", number, purchase.getPurchaseNumber(),
                supplierReturn.getReturnedWeightKg());
        return new RecordedDocument(supplierReturn.getId(), views.of(purchase));
    }

    // E-PROC-05: only while nothing was received (409 INVALID_STATE_TRANSITION otherwise). Under the purchase lock,
    // so a receipt cannot slip in between the check and the change. The reason goes to the audit log
    // (PURCHASE_CANCELLED) once it exists (T3.06); meanwhile it is in the event log line.
    @Transactional
    public PurchaseResponse cancel(UUID purchaseId, String reason) {
        Purchase purchase = lockPurchase(purchaseId);
        if (views.hasAllocations(purchase)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Purchase " + purchase.getPurchaseNumber()
                    + " has payments allocated to it; reverse the payment first.");
        }
        purchase.cancel();
        audit.record(AuditAction.PURCHASE_CANCELLED, "PURCHASE", purchase.getId(), reason,
                Map.of("purchaseNumber", purchase.getPurchaseNumber()));
        log.info("event=purchase_cancelled number={} reason={}", purchase.getPurchaseNumber(), reason);
        return views.of(purchases.saveAndFlush(purchase));         // flush: the response shows the new updatedAt
    }

    private Purchase lockPurchase(UUID purchaseId) {
        return purchases.findByIdForUpdate(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase", purchaseId));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
