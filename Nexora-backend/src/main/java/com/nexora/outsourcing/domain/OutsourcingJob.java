package com.nexora.outsourcing.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import com.nexora.inventory.domain.ProductType;
import com.nexora.shared.domain.AuditableEntity;
import com.nexora.shared.domain.Money;
import com.nexora.shared.domain.Weight;
import com.nexora.shared.error.InvalidProductForOperationException;
import com.nexora.shared.error.InvalidStateTransitionException;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

// Raw silk sent to a manufacturer and warp received back (the aggregate root of outsourcing). Stock is moved by the
// service through InventoryService; this entity holds the state machine and the rule received + wastage <= issued,
// completing the job when they are equal (DOMAIN_RULES section 4, Outsourcing).
@Entity
@Table(name = "outsourcing_jobs")
public class OutsourcingJob extends AuditableEntity {

    @Column(name = "job_number", nullable = false, length = 30, updatable = false)
    private String jobNumber;

    @Column(name = "manufacturer_id", nullable = false, updatable = false)
    private UUID manufacturerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20, updatable = false)
    private ProductType productType;

    @Column(name = "issued_weight_kg", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal issuedWeightKg;

    @Column(name = "received_weight_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal receivedWeightKg;

    @Column(name = "wastage_weight_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal wastageWeightKg;

    // copied at creation and never recalculated (FR-OUT-05)
    @Column(name = "rate_per_kg", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal ratePerKg;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "received_date")
    private LocalDate receivedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(columnDefinition = "text")
    private String notes;

    @ElementCollection
    @CollectionTable(name = "outsourcing_order_items", joinColumns = @JoinColumn(name = "outsourcing_job_id"))
    @BatchSize(size = 100)
    private List<OutsourcingOrderItem> orderItems = new ArrayList<>();

    protected OutsourcingJob() {
        // for JPA only
    }

    // FR-OUT-01: a manufacturer makes silk or kora warp; vuda warp is traded only (FR-INV-10). Order links are
    // optional (FR-OUT-08).
    public static OutsourcingJob create(String jobNumber, UUID manufacturerId, ProductType productType,
                                        BigDecimal issuedWeightKg, BigDecimal ratePerKg, String notes,
                                        List<OutsourcingOrderItem> links) {
        if (productType != ProductType.SILK_WARP && productType != ProductType.KORA_WARP) {
            throw new InvalidProductForOperationException(productType + " cannot be outsourced; a job makes"
                    + " SILK_WARP or KORA_WARP.");
        }
        OutsourcingJob j = new OutsourcingJob();
        j.jobNumber = jobNumber;
        j.manufacturerId = manufacturerId;
        j.productType = productType;
        j.issuedWeightKg = Weight.round(issuedWeightKg);
        j.receivedWeightKg = Weight.zero();
        j.wastageWeightKg = Weight.zero();
        j.ratePerKg = Money.round(ratePerKg);
        j.status = JobStatus.CREATED;
        j.notes = notes;
        j.orderItems.addAll(links);
        return j;
    }

    // CREATED -> MATERIAL_ISSUED. The service writes the OUTSOURCE_ISSUE movement in the same transaction.
    public void issue(LocalDate date) {
        requireStatus(JobStatus.MATERIAL_ISSUED, JobStatus.CREATED);
        issueDate = date;
        status = JobStatus.MATERIAL_ISSUED;
    }

    // One receipt (FR-OUT-03/04): received + wastage may not pass what is still outside; when nothing is left outside
    // the job is COMPLETED. A completed job has nothing outside, so any further receipt is OUTSOURCE_RECEIPT_EXCEEDED.
    public void receive(BigDecimal received, BigDecimal wastage, LocalDate date) {
        if (status == JobStatus.COMPLETED) {
            throw new OutsourceReceiptExceededException(jobNumber, Weight.zero(), received.add(wastage));
        }
        requireStatus(JobStatus.PARTIALLY_RECEIVED, JobStatus.MATERIAL_ISSUED, JobStatus.PARTIALLY_RECEIVED);
        BigDecimal accounted = received.add(wastage);
        if (accounted.compareTo(outsideWeightKg()) > 0) {
            throw new OutsourceReceiptExceededException(jobNumber, outsideWeightKg(), accounted);
        }
        receivedWeightKg = Weight.round(receivedWeightKg.add(received));
        wastageWeightKg = Weight.round(wastageWeightKg.add(wastage));
        receivedDate = receivedDate == null || date.isAfter(receivedDate) ? date : receivedDate;
        status = outsideWeightKg().signum() == 0 ? JobStatus.COMPLETED : JobStatus.PARTIALLY_RECEIVED;
    }

    // CREATED -> CANCELLED; once material is out, the job is finished by receipts (FR-OUT-06).
    public void cancel() {
        requireStatus(JobStatus.CANCELLED, JobStatus.CREATED);
        status = JobStatus.CANCELLED;
    }

    // issued - received - wastage once issued, 0 before (and after a cancel).
    public BigDecimal outsideWeightKg() {
        return issueDate == null ? Weight.zero() : issuedWeightKg.subtract(receivedWeightKg).subtract(wastageWeightKg);
    }

    // received x the job's historical rate (FR-OUT-05).
    public BigDecimal payableAmount() {
        return Money.round(receivedWeightKg.multiply(ratePerKg));
    }

    private void requireStatus(JobStatus to, JobStatus... allowed) {
        for (JobStatus s : allowed) {
            if (status == s) {
                return;
            }
        }
        throw new InvalidStateTransitionException("Job " + jobNumber, status, to);
    }

    public String getJobNumber() {
        return jobNumber;
    }

    public UUID getManufacturerId() {
        return manufacturerId;
    }

    public ProductType getProductType() {
        return productType;
    }

    public BigDecimal getIssuedWeightKg() {
        return issuedWeightKg;
    }

    public BigDecimal getReceivedWeightKg() {
        return receivedWeightKg;
    }

    public BigDecimal getWastageWeightKg() {
        return wastageWeightKg;
    }

    public BigDecimal getRatePerKg() {
        return ratePerKg;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getReceivedDate() {
        return receivedDate;
    }

    public JobStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public List<OutsourcingOrderItem> getOrderItems() {
        return orderItems;
    }
}
