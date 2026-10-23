package com.nexora.outsourcing.application;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.application.AllocationQueryService;
import com.nexora.finance.domain.PaymentPurpose;
import com.nexora.outsourcing.api.dto.ManufacturerPayableResponse;
import com.nexora.outsourcing.api.dto.ManufacturerPayableResponse.JobPayable;
import com.nexora.outsourcing.api.dto.ManufacturerPayableResponse.OpeningPayable;
import com.nexora.outsourcing.domain.JobStatus;
import com.nexora.outsourcing.domain.OutsourcingJob;
import com.nexora.outsourcing.infrastructure.OutsourcingJobRepository;
import com.nexora.parties.application.PartyRef;
import com.nexora.parties.application.PartyService;
import com.nexora.parties.domain.PartyRole;
import com.nexora.shared.domain.Money;
import com.nexora.shared.domain.PaymentStatus;

// What is owed to one manufacturer (FR-PAY-20): always derived, never stored.
@Service
public class ManufacturerPayableQuery {

    private final OutsourcingJobRepository jobs;
    private final AllocationQueryService allocations;
    private final PartyService parties;

    public ManufacturerPayableQuery(OutsourcingJobRepository jobs, AllocationQueryService allocations,
                                    PartyService parties) {
        this.jobs = jobs;
        this.allocations = allocations;
        this.parties = parties;
    }

    // E-PAY-09. 404 unknown, 422 not a manufacturer; an inactive manufacturer's payable is still shown.
    @Transactional(readOnly = true)
    public ManufacturerPayableResponse forManufacturer(UUID manufacturerId) {
        PartyRef manufacturer = parties.requireRole(manufacturerId, PartyRole.MANUFACTURER);
        List<OutsourcingJob> owed = jobs.findByManufacturerIdAndStatusInOrderByIssueDateAscJobNumberAsc(manufacturerId,
                EnumSet.of(JobStatus.PARTIALLY_RECEIVED, JobStatus.COMPLETED));
        Map<UUID, BigDecimal> allocated = allocations.allocatedByJob(owed.stream().map(OutsourcingJob::getId).toList());
        List<JobPayable> open = owed.stream().map(j -> {
            BigDecimal payable = j.payableAmount();
            BigDecimal paid = allocated.getOrDefault(j.getId(), Money.zero());
            BigDecimal outstanding = payable.subtract(paid);
            return new JobPayable(j.getId(), j.getJobNumber(), j.getIssueDate(), j.getReceivedWeightKg(),
                    j.getRatePerKg(), payable, paid, outstanding, PaymentStatus.of(paid, outstanding));
        }).filter(j -> j.outstanding().signum() != 0).toList();
        List<OpeningPayable> openings = allocations.openOpeningObligations(manufacturerId, PaymentPurpose.MANUFACTURER)
                .stream().map(o -> new OpeningPayable(o.id(), o.obligationDate(), o.referenceNote(), o.originalAmount(),
                        o.allocated(), o.outstanding())).toList();
        BigDecimal total = Stream.concat(open.stream().map(JobPayable::outstanding),
                openings.stream().map(OpeningPayable::outstanding)).reduce(Money.zero(), BigDecimal::add);
        return new ManufacturerPayableResponse(manufacturer, total, open, openings);
    }
}
