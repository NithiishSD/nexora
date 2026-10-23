package com.nexora.workforce.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.application.AllocationQueryService;
import com.nexora.finance.domain.RecordStatus;
import com.nexora.parties.application.PartyRef;
import com.nexora.parties.application.PartyService;
import com.nexora.parties.application.WorkerRates;
import com.nexora.parties.domain.PartyRole;
import com.nexora.production.application.ProductionService;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.audit.AuditAction;
import com.nexora.shared.audit.AuditService;
import com.nexora.shared.domain.Money;
import com.nexora.shared.domain.PaymentStatus;
import com.nexora.shared.error.FieldValidationException;
import com.nexora.shared.error.ResourceNotFoundException;
import com.nexora.workforce.api.dto.RecordWorkRequest;
import com.nexora.workforce.api.dto.WorkRecordListResponse;
import com.nexora.workforce.api.dto.WorkRecordResponse;
import com.nexora.workforce.domain.WorkRecord;
import com.nexora.workforce.domain.WorkType;
import com.nexora.workforce.infrastructure.WorkRecordRepository;

// Work record use cases: record, view, list, reverse. One public method = one use case = one transaction.
@Service
public class WorkRecordService {

    private static final Logger log = LoggerFactory.getLogger(WorkRecordService.class);
    private static final Set<String> SORTABLE = Set.of("workDate", "amount", "createdAt");

    private final WorkRecordRepository records;
    private final PartyService parties;
    private final ProductionService production;
    private final AllocationQueryService allocations;
    private final AuditService audit;
    private final Clock clock;

    public WorkRecordService(WorkRecordRepository records, PartyService parties, ProductionService production,
                             AllocationQueryService allocations, AuditService audit, Clock clock) {
        this.records = records;
        this.parties = parties;
        this.production = production;
        this.allocations = allocations;
        this.audit = audit;
        this.clock = clock;
    }

    // E-WORK-01. Worker active with the WORKER role (404/422), batch known (404), rate from the request or the
    // worker's default for the type (none -> 400 on rate), measure checked by the record (400).
    @Transactional
    public WorkRecordResponse record(RecordWorkRequest r) {
        parties.requireActiveWithRole(r.workerId(), PartyRole.WORKER);
        if (r.productionBatchId() != null && production.batchNumbers(List.of(r.productionBatchId())).isEmpty()) {
            throw new ResourceNotFoundException("Production batch", r.productionBatchId());
        }
        BigDecimal rate = r.rate() != null ? r.rate() : defaultRate(r.workerId(), r.workType());
        WorkRecord record = records.save(WorkRecord.record(r.workerId(), r.workDate(), r.workType(), r.hours(),
                r.quantityKg(), rate, r.productionBatchId(), blankToNull(r.notes())));
        log.info("event=work_recorded type={} rate={} amount={}", record.getWorkType(), record.getRate(),
                record.getAmount());
        return views(List.of(record)).getFirst();
    }

    @Transactional(readOnly = true)
    public WorkRecordResponse get(UUID id) {
        return views(List.of(records.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Work record", id)))).getFirst();
    }

    // E-WORK-02. paymentStatus is derived, so its matching ids come from the allocations of every row that passes
    // the other filters; totalAmount sums every matching RECORDED row (not just the page).
    // ponytail: loads all matching rows for the total and the paymentStatus filter; a SQL aggregate past ~100k records.
    @Transactional(readOnly = true)
    public WorkRecordListResponse list(UUID workerId, WorkType workType, RecordStatus status,
                                       PaymentStatus paymentStatus, UUID productionBatchId, LocalDate from,
                                       LocalDate to, Pageable pageable) {
        ListParameters.requireSortable(pageable, SORTABLE);
        ListParameters.requireDateRange(from, to);
        Specification<WorkRecord> filter = (root, query, cb) -> cb.conjunction();
        for (Specification<WorkRecord> part : Arrays.asList(equal("workerId", workerId), equal("workType", workType),
                equal("status", status), equal("productionBatchId", productionBatchId),
                from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("workDate"), from),
                to == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("workDate"), to))) {
            if (part != null) {
                filter = filter.and(part);
            }
        }
        List<WorkRecord> matching = records.findAll(filter);
        if (paymentStatus != null) {
            Map<UUID, BigDecimal> allocated = allocations.allocatedByWorkRecord(
                    matching.stream().map(WorkRecord::getId).toList());
            matching = matching.stream().filter(w -> settlement(w, allocated).status() == paymentStatus).toList();
            List<UUID> ids = matching.stream().map(WorkRecord::getId).toList();
            filter = filter.and((root, query, cb) -> ids.isEmpty() ? cb.disjunction() : root.get("id").in(ids));
        }
        BigDecimal total = matching.stream().filter(w -> w.getStatus() == RecordStatus.RECORDED)
                .map(WorkRecord::getAmount).reduce(Money.zero(), BigDecimal::add);
        Page<WorkRecord> page = records.findAll(filter, pageable);
        Map<UUID, WorkRecordResponse> rows = views(page.getContent()).stream()
                .collect(Collectors.toMap(WorkRecordResponse::id, v -> v));
        return new WorkRecordListResponse(PageResponse.from(page.map(w -> rows.get(w.getId()))), total);
    }

    // E-WORK-04. Lock order (ADR-028): worker party row (the same lock a payment takes first) -> record row; so a
    // payment cannot allocate to the record between the allocation check and the reversal.
    @Transactional
    public WorkRecordResponse reverse(UUID id, String reason) {
        UUID workerId = records.findWorkerId(id).orElseThrow(() -> new ResourceNotFoundException("Work record", id));
        parties.lockForSettlement(workerId);
        WorkRecord record = records.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Work record", id));
        record.reverse(reason, allocations.allocatedByWorkRecord(List.of(id)).containsKey(id), clock.instant());
        audit.record(AuditAction.WORK_RECORD_REVERSED, "WORK_RECORD", record.getId(), reason,
                Map.of("workType", record.getWorkType().name(), "amount", record.getAmount().toPlainString()));
        log.info("event=work_record_reversed id={} amount={}", record.getId(), record.getAmount());
        return views(List.of(records.saveAndFlush(record))).getFirst();
    }

    // ---- helpers --------------------------------------------------------------------------

    private BigDecimal defaultRate(UUID workerId, WorkType type) {
        WorkerRates rates = parties.workerRates(workerId);
        BigDecimal rate = type == WorkType.ROLLING ? rates.rollingPerHour() : rates.warpingPerKg();
        if (rate == null) {
            throw new FieldValidationException("rate", "The worker has no default " + type.name().toLowerCase()
                    + " rate; enter the rate.");
        }
        return rate;
    }

    // Workers, batch numbers and allocations of all rows, one query each.
    private List<WorkRecordResponse> views(List<WorkRecord> rows) {
        Map<UUID, PartyRef> workers = parties.getRefs(rows.stream().map(WorkRecord::getWorkerId).distinct().toList());
        Map<UUID, String> batches = production.batchNumbers(rows.stream().map(WorkRecord::getProductionBatchId)
                .filter(Objects::nonNull).distinct().toList());
        Map<UUID, BigDecimal> allocated = allocations.allocatedByWorkRecord(rows.stream().map(WorkRecord::getId)
                .toList());
        return rows.stream().map(w -> {
            Settlement pay = settlement(w, allocated);
            return new WorkRecordResponse(w.getId(), workers.get(w.getWorkerId()), w.getWorkDate(), w.getWorkType(),
                    w.getHours(), w.getQuantityKg(), w.getRate(), w.getAmount(), pay.allocated(), pay.pending(),
                    pay.status(), w.getStatus(), w.getReversedAt(), w.getReversalReason(),
                    w.getProductionBatchId() == null ? null : new WorkRecordResponse.BatchRef(w.getProductionBatchId(),
                            batches.get(w.getProductionBatchId())),
                    w.getNotes(), w.getCreatedAt(), w.getUpdatedAt(), w.getCreatedBy());
        }).toList();
    }

    // The payment side of a record (FR-WORK-04); a REVERSED record shows UNPAID with nothing pending.
    private static Settlement settlement(WorkRecord w, Map<UUID, BigDecimal> allocatedById) {
        if (w.getStatus() != RecordStatus.RECORDED) {
            return new Settlement(Money.zero(), Money.zero(), PaymentStatus.UNPAID);
        }
        BigDecimal allocated = allocatedById.getOrDefault(w.getId(), Money.zero());
        BigDecimal pending = w.getAmount().subtract(allocated);
        return new Settlement(allocated, pending, PaymentStatus.of(allocated, pending));
    }

    private record Settlement(BigDecimal allocated, BigDecimal pending, PaymentStatus status) {
    }

    private static Specification<WorkRecord> equal(String attribute, Object value) {
        return value == null ? null : (root, query, cb) -> cb.equal(root.get(attribute), value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
