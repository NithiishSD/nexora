package com.nexora.finance.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.api.dto.OpeningObligationRequest;
import com.nexora.finance.api.dto.OpeningObligationResponse;
import com.nexora.finance.domain.OpeningObligation;
import com.nexora.finance.domain.PaymentPurpose;
import com.nexora.finance.infrastructure.ObligationQueryRepository;
import com.nexora.finance.infrastructure.OpeningObligationRepository;
import com.nexora.parties.application.PartyRef;
import com.nexora.parties.application.PartyService;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.audit.AuditAction;
import com.nexora.shared.audit.AuditService;
import com.nexora.shared.domain.Money;

// Go-live data entry of receivables and payables from the paper ledger (FR-PAY-22). Payments then settle them
// oldest-first exactly like orders and purchases; they never touch revenue, expenses, purchases or stock.
@Service
public class OpeningObligationService {

    private static final Logger log = LoggerFactory.getLogger(OpeningObligationService.class);
    private static final Set<String> SORTABLE = Set.of("obligationDate", "originalAmount");

    private final OpeningObligationRepository openings;
    private final ObligationQueryRepository obligations;
    private final PartyService parties;
    private final AuditService audit;
    private final Clock clock;

    public OpeningObligationService(OpeningObligationRepository openings, ObligationQueryRepository obligations,
                                    PartyService parties, AuditService audit, Clock clock) {
        this.openings = openings;
        this.obligations = obligations;
        this.parties = parties;
        this.audit = audit;
        this.clock = clock;
    }

    // E-PAY-11. The party must hold the role of the purpose and be active (422). The party row is locked, so the entry
    // cannot interleave with a payment of the same party that is allocating oldest-first (ADR-027).
    @Transactional
    public OpeningObligationResponse create(OpeningObligationRequest r) {
        PartyRef party = parties.requireActiveWithRole(r.partyId(), r.purpose().role());
        parties.lockForSettlement(r.partyId());
        OpeningObligation o = openings.save(OpeningObligation.enter(r.partyId(), r.purpose(), r.obligationDate(),
                r.dueDate(), Money.round(r.originalAmount()), r.referenceNote().strip(), LocalDate.now(clock)));
        audit.record(AuditAction.OPENING_BALANCE_IMPORTED, "OPENING_OBLIGATION", o.getId(), o.getReferenceNote(),
                Map.of("purpose", o.getPurpose().name(), "amount", o.getOriginalAmount().toPlainString(),
                        "obligationDate", o.getObligationDate().toString()));
        log.info("event=opening_obligation_entered purpose={} amount={}", o.getPurpose(), o.getOriginalAmount());
        return toResponse(o, party, Money.zero());
    }

    // E-PAY-12. outstandingOnly is derived, so it filters on the ids SQL reports as still open.
    @Transactional(readOnly = true)
    public PageResponse<OpeningObligationResponse> list(UUID partyId, PaymentPurpose purpose, Boolean outstandingOnly,
                                                        Pageable pageable) {
        ListParameters.requireSortable(pageable, SORTABLE);
        Specification<OpeningObligation> filter = (root, query, cb) -> cb.conjunction();
        List<UUID> open = Boolean.TRUE.equals(outstandingOnly) ? obligations.openOpeningObligationIds() : null;
        for (Specification<OpeningObligation> part : Arrays.<Specification<OpeningObligation>>asList(
                partyId == null ? null : (root, query, cb) -> cb.equal(root.get("partyId"), partyId),
                purpose == null ? null : (root, query, cb) -> cb.equal(root.get("purpose"), purpose),
                open == null ? null : (root, query, cb) -> open.isEmpty() ? cb.disjunction() : root.get("id").in(open))) {
            if (part != null) {
                filter = filter.and(part);
            }
        }
        Page<OpeningObligation> page = openings.findAll(filter, pageable);
        Map<UUID, PartyRef> refs = parties.getRefs(page.getContent().stream().map(OpeningObligation::getPartyId)
                .distinct().toList());
        Map<UUID, BigDecimal> allocated = obligations.allocatedByOpeningObligation(page.getContent().stream()
                .map(OpeningObligation::getId).toList());
        return PageResponse.from(page.map(o -> toResponse(o, refs.get(o.getPartyId()),
                allocated.getOrDefault(o.getId(), Money.zero()))));
    }

    private static OpeningObligationResponse toResponse(OpeningObligation o, PartyRef party, BigDecimal allocated) {
        return new OpeningObligationResponse(o.getId(), o.getPurpose(), party, o.getObligationDate(), o.getDueDate(),
                o.getOriginalAmount(), allocated, o.getOriginalAmount().subtract(allocated), o.getReferenceNote(),
                o.getCreatedAt(), o.getUpdatedAt(), o.getCreatedBy());
    }
}
