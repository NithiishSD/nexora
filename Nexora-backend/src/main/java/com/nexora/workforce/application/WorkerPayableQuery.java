package com.nexora.workforce.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.application.AllocationQueryService;
import com.nexora.finance.domain.PaymentPurpose;
import com.nexora.finance.domain.RecordStatus;
import com.nexora.parties.application.PartyRef;
import com.nexora.parties.application.PartyService;
import com.nexora.parties.domain.PartyRole;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.domain.Money;
import com.nexora.shared.domain.PaymentStatus;
import com.nexora.workforce.api.dto.WorkerPayableResponse;
import com.nexora.workforce.domain.WorkRecord;
import com.nexora.workforce.infrastructure.WorkRecordRepository;

// What is owed to one worker (FR-PAY-21): always derived from records and allocations, never stored.
@Service
public class WorkerPayableQuery {

    private final WorkRecordRepository records;
    private final AllocationQueryService allocations;
    private final PartyService parties;

    public WorkerPayableQuery(WorkRecordRepository records, AllocationQueryService allocations, PartyService parties) {
        this.records = records;
        this.allocations = allocations;
        this.parties = parties;
    }

    // E-PAY-10. 404 unknown, 422 not a worker, 400 from after to.
    @Transactional(readOnly = true)
    public WorkerPayableResponse forWorker(UUID workerId, LocalDate from, LocalDate to) {
        PartyRef worker = parties.requireRole(workerId, PartyRole.WORKER);
        ListParameters.requireDateRange(from, to);
        Specification<WorkRecord> filter = (root, query, cb) -> cb.and(cb.equal(root.get("workerId"), workerId),
                cb.equal(root.get("status"), RecordStatus.RECORDED));
        if (from != null) {
            filter = filter.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("workDate"), from));
        }
        if (to != null) {
            filter = filter.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("workDate"), to));
        }
        List<WorkRecord> work = records.findAll(filter, Sort.by("workDate", "createdAt"));
        Map<UUID, BigDecimal> allocated = allocations.allocatedByWorkRecord(work.stream().map(WorkRecord::getId).toList());
        List<WorkerPayableResponse.Record> rows = work.stream().map(w -> {
            BigDecimal paid = allocated.getOrDefault(w.getId(), Money.zero());
            BigDecimal outstanding = w.getAmount().subtract(paid);
            return new WorkerPayableResponse.Record(w.getId(), w.getWorkDate(), w.getWorkType(), w.getAmount(), paid,
                    outstanding, PaymentStatus.of(paid, outstanding));
        }).toList();
        BigDecimal value = rows.stream().map(WorkerPayableResponse.Record::amount).reduce(Money.zero(), BigDecimal::add);
        BigDecimal paid = rows.stream().map(WorkerPayableResponse.Record::allocated)
                .reduce(Money.zero(), BigDecimal::add);
        List<WorkerPayableResponse.OpeningPayable> openings = allocations
                .openOpeningObligations(workerId, PaymentPurpose.WORKER).stream()
                .map(o -> new WorkerPayableResponse.OpeningPayable(o.id(), o.obligationDate(), o.referenceNote(),
                        o.originalAmount(), o.allocated(), o.outstanding())).toList();
        return new WorkerPayableResponse(worker, from, to, value, paid, value.subtract(paid), rows, openings);
    }
}
