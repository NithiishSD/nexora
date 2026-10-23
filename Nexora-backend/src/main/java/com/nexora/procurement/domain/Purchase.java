package com.nexora.procurement.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.hibernate.annotations.BatchSize;

import com.nexora.inventory.domain.ProductType;
import com.nexora.shared.domain.AuditableEntity;
import com.nexora.shared.domain.Money;
import com.nexora.shared.domain.Weight;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.error.FieldValidationException;
import com.nexora.shared.error.InvalidProductForOperationException;
import com.nexora.shared.error.InvalidStateTransitionException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

// What was agreed with a supplier: product, weight, rate and terms (DOMAIN_RULES 4, Procurement). The aggregate
// root of procurement: receipts and returns are created only through it, so its rules always hold.
// Rate and ordered weight are historical and never change (FR-PROC-05): there is no method that changes them.
@Entity
@Table(name = "purchases")
public class Purchase extends AuditableEntity {

    @Column(name = "purchase_number", nullable = false, length = 30, updatable = false)
    private String purchaseNumber;

    // Another module's record: referenced by id only, never by a JPA relation (CONVENTIONS section 1).
    @Column(name = "supplier_id", nullable = false, updatable = false)
    private UUID supplierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20, updatable = false)
    private ProductType productType;

    @Column(name = "purchase_date", nullable = false, updatable = false)
    private LocalDate purchaseDate;

    @Column(name = "ordered_weight_kg", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal orderedWeightKg;

    @Column(name = "rate_per_kg", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal ratePerKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 10, updatable = false)
    private PaymentType paymentType;

    @Column(name = "credit_days", nullable = false, updatable = false)
    private int creditDays;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "bill_reference", length = 100)
    private String billReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PurchaseStatus status;

    @Column(columnDefinition = "text")
    private String notes;

    // The purchase owns its receipts and returns: a new one added here is inserted with the purchase
    // (CascadeType.PERSIST). No REMOVE and no orphan removal: they are history and are never deleted.
    // @BatchSize: a page of purchases loads the receipts of up to 100 purchases in one query (no N+1).
    @OneToMany(mappedBy = "purchase", cascade = CascadeType.PERSIST)
    @OrderBy("receiptDate asc, receiptNumber asc")
    @BatchSize(size = 100)
    private List<MaterialReceipt> receipts = new ArrayList<>();

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.PERSIST)
    @OrderBy("returnDate asc, returnNumber asc")
    @BatchSize(size = 100)
    private List<SupplierReturn> returns = new ArrayList<>();

    protected Purchase() {
        // for JPA only
    }

    // Factory: a new purchase is ORDERED; the due date is computed here, never sent by the client (FR-PROC-04).
    public static Purchase create(String purchaseNumber, UUID supplierId, ProductType productType,
                                  LocalDate purchaseDate, BigDecimal orderedWeightKg, BigDecimal ratePerKg,
                                  PaymentType paymentType, int creditDays, String billReference, String notes) {
        // only trading stock is bought; silk and kora warp are made (FR-PROC-17 AC2)
        if (!productType.isTradingStock()) {
            throw new InvalidProductForOperationException(productType + " cannot be purchased; only RAW_SILK or VUDA_WARP.");
        }
        requireKg("orderedWeightKg", orderedWeightKg);
        if (ratePerKg.signum() < 0 || ratePerKg.stripTrailingZeros().scale() > Money.SCALE) {
            throw new FieldValidationException("ratePerKg", "Rate must be 0.00 or more with at most 2 decimals.");
        }
        if (creditDays < 0) {
            throw new FieldValidationException("creditDays", "Credit days cannot be negative.");
        }
        if (paymentType == PaymentType.CASH && creditDays != 0) {
            throw new FieldValidationException("creditDays", "A cash purchase has no credit days.");
        }
        Purchase p = new Purchase();
        p.purchaseNumber = purchaseNumber;
        p.supplierId = supplierId;
        p.productType = productType;
        p.purchaseDate = purchaseDate;
        p.orderedWeightKg = Weight.round(orderedWeightKg);
        p.ratePerKg = Money.round(ratePerKg);
        p.paymentType = paymentType;
        p.creditDays = creditDays;
        p.dueDate = purchaseDate.plusDays(creditDays);
        p.billReference = billReference;
        p.notes = notes;
        p.status = PurchaseStatus.ORDERED;
        return p;
    }

    // Records what arrived (FR-PROC-08/09/11). The caller must hold the row lock on this purchase, so the
    // "total received <= ordered" check cannot race with another receipt.
    public MaterialReceipt recordReceipt(String receiptNumber, LocalDate receiptDate, BigDecimal receivedKg,
                                         BigDecimal acceptedKg, BigDecimal rejectedKg, String notes) {
        if (status == PurchaseStatus.CANCELLED) {
            throw new InvalidStateTransitionException("Purchase " + purchaseNumber, status, "RECEIVED");
        }
        if (status == PurchaseStatus.FULLY_RECEIVED) {
            throw new PurchaseReceiptExceededException(purchaseNumber, Weight.zero());   // nothing left (AC3)
        }
        requireKg("receivedWeightKg", receivedKg);
        requireKgOrZero("acceptedWeightKg", acceptedKg);
        requireKgOrZero("rejectedWeightKg", rejectedKg);
        if (acceptedKg.add(rejectedKg).compareTo(receivedKg) > 0) {
            throw new FieldValidationException("acceptedWeightKg",
                    "Accepted plus rejected weight cannot be more than the received weight.");
        }
        if (receivedWeightKg().add(receivedKg).compareTo(orderedWeightKg) > 0) {
            throw new PurchaseReceiptExceededException(purchaseNumber, remainingWeightKg());
        }
        MaterialReceipt receipt = MaterialReceipt.of(this, receiptNumber, receiptDate, receivedKg, acceptedKg,
                rejectedKg, notes);
        receipts.add(receipt);
        // the status follows from the totals: everything ordered has arrived -> FULLY_RECEIVED
        status = receivedWeightKg().compareTo(orderedWeightKg) == 0
                ? PurchaseStatus.FULLY_RECEIVED : PurchaseStatus.PARTIALLY_RECEIVED;
        return receipt;
    }

    // Sends material back (FR-PROC-13). At most what was accepted and not yet returned; the stock check itself
    // (current RAW_STOCK may be lower after consumption) is done by the inventory when the movement is recorded.
    public SupplierReturn recordReturn(String returnNumber, LocalDate returnDate, BigDecimal returnedKg, String reason,
                                       String notes) {
        if (status == PurchaseStatus.CANCELLED) {
            throw new InvalidStateTransitionException("Purchase " + purchaseNumber, status, "RETURNED");
        }
        requireKg("returnedWeightKg", returnedKg);
        if (reason == null || reason.isBlank()) {
            throw new FieldValidationException("reason", "A return needs a reason.");
        }
        BigDecimal returnable = acceptedWeightKg().subtract(returnedWeightKg());
        if (returnedKg.compareTo(returnable) > 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_INVENTORY, "Only " + returnable.toPlainString()
                    + " kg of purchase " + purchaseNumber + " can be returned.");
        }
        SupplierReturn supplierReturn = SupplierReturn.of(this, returnNumber, returnDate, returnedKg, reason, notes);
        returns.add(supplierReturn);
        return supplierReturn;
    }

    // Only while nothing has arrived (FR-PROC-12). Payment allocations are checked by the caller once they exist.
    public void cancel() {
        if (status != PurchaseStatus.ORDERED || !receipts.isEmpty()) {
            throw new InvalidStateTransitionException("Purchase " + purchaseNumber, status, PurchaseStatus.CANCELLED);
        }
        status = PurchaseStatus.CANCELLED;
    }

    // The only editable fields (FR-PROC-05): free text that does not change any amount or weight.
    public void updateNotes(String notes, String billReference) {
        this.notes = notes;
        this.billReference = billReference;
    }

    // ---- derived values (computed on read, never stored) ---------------------------------

    // round(ordered kg x rate, 2, HALF_UP) (FR-PROC-03)
    public BigDecimal orderedValue() {
        return Money.round(orderedWeightKg.multiply(ratePerKg));
    }

    public BigDecimal receivedWeightKg() {
        return sumReceipts(MaterialReceipt::getReceivedWeightKg);
    }

    public BigDecimal acceptedWeightKg() {
        return sumReceipts(MaterialReceipt::getAcceptedWeightKg);
    }

    public BigDecimal rejectedWeightKg() {
        return sumReceipts(MaterialReceipt::getRejectedWeightKg);
    }

    public BigDecimal returnedWeightKg() {
        return Weight.round(returns.stream().map(SupplierReturn::getReturnedWeightKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    // Still to arrive; 0 once cancelled.
    public BigDecimal remainingWeightKg() {
        return status == PurchaseStatus.CANCELLED ? Weight.zero() : Weight.round(orderedWeightKg.subtract(receivedWeightKg()));
    }

    // ASSUMPTION Q1 (confirm with the owner): only accepted kg are paid, returns credit the purchase rate.
    // round((accepted - returned) x rate, 2, HALF_UP) (FR-PROC-15).
    public BigDecimal payableAmount() {
        return Money.round(acceptedWeightKg().subtract(returnedWeightKg()).multiply(ratePerKg));
    }

    private BigDecimal sumReceipts(Function<MaterialReceipt, BigDecimal> weight) {
        return Weight.round(receipts.stream().map(weight).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    // > 0 and at most 3 decimals (FR-INV-15): more decimals are rejected, never rounded away.
    private static void requireKg(String field, BigDecimal kg) {
        if (kg == null || kg.signum() <= 0 || kg.stripTrailingZeros().scale() > Weight.SCALE) {
            throw new FieldValidationException(field, "Weight must be greater than 0 with at most 3 decimals.");
        }
    }

    private static void requireKgOrZero(String field, BigDecimal kg) {
        if (kg == null || kg.signum() < 0 || kg.stripTrailingZeros().scale() > Weight.SCALE) {
            throw new FieldValidationException(field, "Weight must be 0 or more with at most 3 decimals.");
        }
    }

    // ---- getters -------------------------------------------------------------------------

    public String getPurchaseNumber() {
        return purchaseNumber;
    }

    public UUID getSupplierId() {
        return supplierId;
    }

    public ProductType getProductType() {
        return productType;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public BigDecimal getOrderedWeightKg() {
        return orderedWeightKg;
    }

    public BigDecimal getRatePerKg() {
        return ratePerKg;
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public int getCreditDays() {
        return creditDays;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getBillReference() {
        return billReference;
    }

    public PurchaseStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public List<MaterialReceipt> getReceipts() {
        return List.copyOf(receipts);
    }

    public List<SupplierReturn> getReturns() {
        return List.copyOf(returns);
    }
}
