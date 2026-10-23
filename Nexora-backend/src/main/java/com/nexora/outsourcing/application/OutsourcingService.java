package com.nexora.outsourcing.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.inventory.application.InventoryService;
import com.nexora.inventory.application.MovementCommand;
import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.orders.application.OrderService;
import com.nexora.outsourcing.api.dto.CreateJobRequest;
import com.nexora.outsourcing.api.dto.IssueJobRequest;
import com.nexora.outsourcing.api.dto.JobLinkRequest;
import com.nexora.outsourcing.api.dto.JobResponse;
import com.nexora.outsourcing.api.dto.ReceiveJobRequest;
import com.nexora.outsourcing.domain.JobStatus;
import com.nexora.outsourcing.domain.OutsourcingJob;
import com.nexora.outsourcing.domain.OutsourcingOrderItem;
import com.nexora.outsourcing.infrastructure.OutsourcingJobRepository;
import com.nexora.parties.application.PartyService;
import com.nexora.parties.domain.PartyRole;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.audit.AuditAction;
import com.nexora.shared.audit.AuditService;
import com.nexora.shared.domain.Weight;
import com.nexora.shared.error.ResourceNotFoundException;
import com.nexora.shared.numbering.BusinessNumberGenerator;

// Outsourcing job use cases: create, view, list, issue, receive, cancel. One public method = one use case = one
// transaction.
@Service
public class OutsourcingService {

    // Reason on the WASTAGE movement of a receipt (05 E-OUT-05); the wastage report tells it apart.
    static final String OUTSOURCING_LOSS = "OUTSOURCING_LOSS";

    private static final Logger log = LoggerFactory.getLogger(OutsourcingService.class);
    private static final Set<String> SORTABLE = Set.of("issueDate", "jobNumber", "createdAt");

    private final OutsourcingJobRepository jobs;
    private final PartyService parties;
    private final OrderService orders;
    private final InventoryService inventory;
    private final BusinessNumberGenerator numbers;
    private final JobViews views;
    private final AuditService audit;
    private final Clock clock;

    public OutsourcingService(OutsourcingJobRepository jobs, PartyService parties, OrderService orders,
                              InventoryService inventory, BusinessNumberGenerator numbers, JobViews views,
                              AuditService audit, Clock clock) {
        this.jobs = jobs;
        this.parties = parties;
        this.orders = orders;
        this.inventory = inventory;
        this.numbers = numbers;
        this.views = views;
        this.audit = audit;
        this.clock = clock;
    }

    // E-OUT-01. The manufacturer must be active with the MANUFACTURER role (422); the JOB number is the last lock
    // taken (ADR-028); no stock moves.
    @Transactional
    public JobResponse create(CreateJobRequest r) {
        parties.requireActiveWithRole(r.manufacturerId(), PartyRole.MANUFACTURER);
        List<OutsourcingOrderItem> links = links(r.productType(), r.orderItems());
        OutsourcingJob job = OutsourcingJob.create(numbers.next("JOB"), r.manufacturerId(), r.productType(),
                r.issuedWeightKg(), r.ratePerKg(), blankToNull(r.notes()), links);
        jobs.save(job);
        log.info("event=job_created number={} product={} issuedKg={}", job.getJobNumber(), job.getProductType(),
                job.getIssuedWeightKg());
        return views.of(job);
    }

    @Transactional(readOnly = true)
    public JobResponse get(UUID id) {
        return views.of(jobs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Outsourcing job", id)));
    }

    // E-OUT-02. from/to filter the issue date.
    @Transactional(readOnly = true)
    public PageResponse<JobResponse> list(UUID manufacturerId, JobStatus status, UUID orderId, LocalDate from,
                                          LocalDate to, Pageable pageable) {
        ListParameters.requireSortable(pageable, SORTABLE);
        ListParameters.requireDateRange(from, to);
        List<UUID> orderItems = orderId == null ? null : orders.itemIdsOf(orderId);
        Specification<OutsourcingJob> filter = (root, query, cb) -> cb.conjunction();
        for (Specification<OutsourcingJob> part : Arrays.<Specification<OutsourcingJob>>asList(
                manufacturerId == null ? null : (root, query, cb) -> cb.equal(root.get("manufacturerId"), manufacturerId),
                status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status),
                from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("issueDate"), from),
                to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("issueDate"), to),
                orderItems == null ? null : (root, query, cb) -> {
                    query.distinct(true);
                    return orderItems.isEmpty() ? cb.disjunction()
                            : root.join("orderItems").get("orderItemId").in(orderItems);
                })) {
            if (part != null) {
                filter = filter.and(part);
            }
        }
        return views.page(jobs.findAll(filter, pageable));
    }

    // E-OUT-04. Lock order (ADR-028): job row -> linked order rows (ascending id) -> raw-silk and external-WIP
    // balances -> MOV number. All or nothing (FR-OUT-09): not enough raw silk -> 409 INSUFFICIENT_INVENTORY, the job
    // stays CREATED and no order is locked.
    @Transactional
    public JobResponse issue(UUID id, IssueJobRequest r) {
        OutsourcingJob job = lock(id);
        LocalDate date = r != null && r.issueDate() != null ? r.issueDate() : LocalDate.now(clock);
        job.issue(date);                                                   // 409 unless CREATED
        orders.lockForProduction(job.getOrderItems().stream().map(OutsourcingOrderItem::getOrderItemId).toList());
        // raw silk leaves RAW_STOCK and is held by the manufacturer as the warp being made (plan decision R3)
        inventory.recordMovement(new MovementCommand(ProductType.RAW_SILK, job.getProductType(),
                MovementType.OUTSOURCE_ISSUE, StockLocation.RAW_STOCK, StockLocation.EXTERNAL_WIP,
                job.getIssuedWeightKg(), date, MovementReferenceType.OUTSOURCING_JOB, job.getId(), job.getJobNumber(),
                null, null));
        log.info("event=job_issued number={} issuedKg={}", job.getJobNumber(), job.getIssuedWeightKg());
        return views.of(jobs.saveAndFlush(job));
    }

    // E-OUT-05. Lock order (ADR-028): job row -> EXTERNAL_WIP and FINISHED_STOCK balances -> MOV numbers. The job
    // checks the receipt first (409 OUTSOURCE_RECEIPT_EXCEEDED / INVALID_STATE_TRANSITION, nothing written); then the
    // warp moves EXTERNAL_WIP -> FINISHED_STOCK and the loss EXTERNAL_WIP -> WASTAGE, all or nothing (FR-OUT-09).
    @Transactional
    public JobResponse receive(UUID id, ReceiveJobRequest r) {
        OutsourcingJob job = lock(id);
        job.receive(Weight.round(r.receivedWeightKg()), Weight.round(r.wastageWeightKg()), r.receiptDate());
        List<MovementCommand> moves = new ArrayList<>();
        moves.add(new MovementCommand(job.getProductType(), null, MovementType.OUTSOURCE_RECEIPT,
                StockLocation.EXTERNAL_WIP, StockLocation.FINISHED_STOCK, r.receivedWeightKg(), r.receiptDate(),
                MovementReferenceType.OUTSOURCING_JOB, job.getId(), job.getJobNumber(), null, blankToNull(r.notes())));
        if (r.wastageWeightKg().signum() > 0) {
            moves.add(new MovementCommand(job.getProductType(), null, MovementType.WASTAGE, StockLocation.EXTERNAL_WIP,
                    StockLocation.WASTAGE, r.wastageWeightKg(), r.receiptDate(), MovementReferenceType.OUTSOURCING_JOB,
                    job.getId(), job.getJobNumber(), OUTSOURCING_LOSS, null));
        }
        inventory.recordMovements(moves);
        log.info("event=job_received number={} receivedKg={} wastageKg={} status={}", job.getJobNumber(),
                r.receivedWeightKg(), r.wastageWeightKg(), job.getStatus());
        return views.of(jobs.saveAndFlush(job));
    }

    // E-OUT-06: only from CREATED (nothing was issued); an issued job is finished by its receipts.
    @Transactional
    public JobResponse cancel(UUID id, String reason) {
        OutsourcingJob job = lock(id);
        job.cancel();
        audit.record(AuditAction.OUTSOURCING_CANCELLED, "OUTSOURCING_JOB", job.getId(), reason,
                Map.of("jobNumber", job.getJobNumber(), "issuedWeightKg", job.getIssuedWeightKg().toPlainString()));
        log.info("event=job_cancelled number={} reason={}", job.getJobNumber(), reason);
        return views.of(jobs.saveAndFlush(job));
    }

    // ---- helpers --------------------------------------------------------------------------

    // Links checked by orders (400 twice, 404 unknown, 422 other warp type, 409 order not workable).
    private List<OutsourcingOrderItem> links(ProductType productType, List<JobLinkRequest> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        orders.checkWorkLinks(productType, requested.stream().map(JobLinkRequest::orderItemId).toList(), "job");
        return requested.stream()
                .map(l -> new OutsourcingOrderItem(l.orderItemId(), Weight.round(l.allocatedWeightKg()))).toList();
    }

    private OutsourcingJob lock(UUID id) {
        return jobs.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Outsourcing job", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
