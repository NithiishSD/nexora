package com.nexora.parties.application;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.parties.api.PartyMapper;
import com.nexora.parties.api.dto.CreatePartyRequest;
import com.nexora.parties.api.dto.PartyResponse;
import com.nexora.parties.api.dto.PartySummaryResponse;
import com.nexora.parties.api.dto.UpdatePartyRequest;
import com.nexora.parties.domain.InvalidPartyRoleException;
import com.nexora.parties.domain.Party;
import com.nexora.parties.domain.PartyDetails;
import com.nexora.parties.domain.PartyRole;
import com.nexora.parties.domain.PartyStatus;
import com.nexora.parties.infrastructure.PartyRepository;
import com.nexora.shared.audit.AuditAction;
import com.nexora.shared.audit.AuditService;
import com.nexora.parties.infrastructure.PartySpecifications;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.error.ResourceNotFoundException;
import com.nexora.shared.numbering.BusinessNumberGenerator;

// Party use cases. Other modules use this service (never PartyRepository) to look parties up.
@Service
public class PartyService {

    private static final Logger log = LoggerFactory.getLogger(PartyService.class);

    private final PartyRepository parties;
    private final BusinessNumberGenerator numbers;
    private final AuditService audit;

    public PartyService(PartyRepository parties, BusinessNumberGenerator numbers, AuditService audit) {
        this.audit = audit;
        this.parties = parties;
        this.numbers = numbers;
    }

    // One transaction: if anything fails, the PTY counter increment rolls back too (gap-free codes).
    @Transactional
    public PartyResponse create(CreatePartyRequest request) {
        String code = numbers.next("PTY");                              // PTY-2026-0001, row-locked counter
        Party party = Party.create(code, toDetails(request), request.roles(), PartyMapper.toPhones(request.phones()));
        Party saved = parties.save(party);                              // created_by comes from the JWT (auditing)
        log.info("event=party_created number={}", code);                // no names or phone numbers in logs
        return PartyMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PartyResponse get(UUID id) {
        return PartyMapper.toResponse(find(id));
    }

    // Allowed sort properties (05-API-SPEC E-PARTY-03); anything else is a client error, not a 500.
    private static final Set<String> SORTABLE = Set.of("name", "partyCode", "createdAt");

    // Filtered, paged list. Roles and phones of the page are loaded in batches (@BatchSize on Party),
    // so the number of SQL statements does not grow with the page size (no N+1).
    @Transactional(readOnly = true)
    public PageResponse<PartySummaryResponse> list(PartyRole role, PartyStatus status, String search, Pageable pageable) {
        ListParameters.requireSortable(pageable, SORTABLE);
        // Start with "no restriction" and AND in only the filters the caller used.
        Specification<Party> filter = (root, query, cb) -> cb.conjunction();
        for (Specification<Party> part : Arrays.asList(PartySpecifications.hasRole(role),
                PartySpecifications.hasStatus(status), PartySpecifications.matches(search))) {
            if (part != null) {
                filter = filter.and(part);
            }
        }
        return PageResponse.from(parties.findAll(filter, pageable).map(PartyMapper::toSummary));
    }

    // PATCH: start from the current values and apply only what the request contains. The @Version check
    // on commit turns a concurrent update into 409 CONCURRENT_MODIFICATION.
    @Transactional
    public PartyResponse update(UUID id, UpdatePartyRequest r) {
        Party party = find(id);
        party.update(new PartyDetails(
                r.name() != null ? r.name() : party.getName(),
                text(r.addressLine(), party.getAddressLine()),
                text(r.area(), party.getArea()),
                text(r.city(), party.getCity()),
                text(r.district(), party.getDistrict()),
                text(r.state(), party.getState()),
                text(r.pincode(), party.getPincode()),
                text(r.note(), party.getNote()),
                r.defaultCreditDays() != null ? r.defaultCreditDays() : party.getDefaultCreditDays(),
                r.customerCreditDays() != null ? r.customerCreditDays() : party.getCustomerCreditDays(),
                r.defaultRollingRatePerHour() != null ? r.defaultRollingRatePerHour() : party.getDefaultRollingRatePerHour(),
                r.defaultWarpingRatePerKg() != null ? r.defaultWarpingRatePerKg() : party.getDefaultWarpingRatePerKg()));
        if (r.phones() != null) {
            party.replacePhones(PartyMapper.toPhones(r.phones()));    // the whole list is replaced
        }
        return PartyMapper.toResponse(parties.saveAndFlush(party));   // flush: response shows the new updatedAt
    }

    @Transactional
    public PartyResponse addRole(UUID id, PartyRole role) {
        Party party = find(id);
        party.addRole(role);
        return PartyMapper.toResponse(parties.saveAndFlush(party));
    }

    @Transactional
    public PartyResponse updateCustomerTerms(UUID id, Integer creditPeriodDays) {
        Party party = find(id);
        party.updateCustomerTerms(creditPeriodDays);
        log.info("event=customer_terms_updated number={} creditDays={}", party.getPartyCode(), creditPeriodDays);
        return PartyMapper.toResponse(parties.saveAndFlush(party));
    }

    // Audit rows for these two actions (PARTY_DEACTIVATED / PARTY_ACTIVATED) arrive with audit_log in M3.
    @Transactional
    public PartyResponse deactivate(UUID id) {
        Party party = find(id);
        party.deactivate();                                            // 409 if already inactive
        audit.record(AuditAction.PARTY_DEACTIVATED, "PARTY", party.getId(), null, Map.of("partyCode", party.getPartyCode()));
        log.info("event=party_deactivated number={}", party.getPartyCode());
        return PartyMapper.toResponse(parties.saveAndFlush(party));
    }

    @Transactional
    public PartyResponse activate(UUID id) {
        Party party = find(id);
        party.activate();                                              // 409 if already active
        audit.record(AuditAction.PARTY_ACTIVATED, "PARTY", party.getId(), null, Map.of("partyCode", party.getPartyCode()));
        log.info("event=party_activated number={}", party.getPartyCode());
        return PartyMapper.toResponse(parties.saveAndFlush(party));
    }

    // ---- contract for other modules (03-ARCHITECTURE 5.2) -------------------------------------

    // The ONE check every module calls before using a party in a new transaction (FR-PARTY-11/12):
    // unknown -> 404 RESOURCE_NOT_FOUND, missing role -> 422 INVALID_PARTY_ROLE, inactive -> 422 PARTY_INACTIVE.
    // Joins the caller's transaction (default propagation), so it sees the same data the use case sees.
    @Transactional(readOnly = true)
    public PartyRef requireActiveWithRole(UUID partyId, PartyRole role) {
        Party party = find(partyId);
        party.requireUsableAs(role);
        return toRef(party);
    }

    // Locks the party row until the caller's transaction ends (ADR-027): payments, payment reversals and the order
    // changes that depend on allocations (confirm with advance, edit, cancel) of ONE party run one after the other,
    // so an outstanding amount or an advance can never be used twice. Lock order (ADR-028): idempotency key ->
    // party -> aggregate -> balances -> accounts -> numbers. MANDATORY: a lock outside a use case would end at once.
    @Transactional(propagation = Propagation.MANDATORY)
    public PartyRef lockForSettlement(UUID partyId) {
        return toRef(parties.findByIdForUpdate(partyId).orElseThrow(() -> new ResourceNotFoundException("Party", partyId)));
    }

    // For reading another module's view of a party (payables, outstanding): the role must match (422), but an
    // inactive party's history is still shown.
    @Transactional(readOnly = true)
    public PartyRef requireRole(UUID partyId, PartyRole role) {
        Party party = find(partyId);
        if (!party.hasRole(role)) {
            throw new InvalidPartyRoleException(party.getPartyCode(), role);
        }
        return toRef(party);
    }

    // Code and name for response DTOs of other modules; works for inactive parties too (history stays visible).
    @Transactional(readOnly = true)
    public PartyRef getRef(UUID partyId) {
        return toRef(find(partyId));
    }

    // Code and name of many parties in ONE query, for list responses of other modules (no query per row).
    @Transactional(readOnly = true)
    public Map<UUID, PartyRef> getRefs(Collection<UUID> partyIds) {
        return parties.findAllById(partyIds).stream()
                .collect(Collectors.toMap(Party::getId, PartyService::toRef));
    }

    // A supplier's usual credit period, used to pre-fill a new purchase (FR-PROC-01); null when none is set.
    @Transactional(readOnly = true)
    public Integer defaultCreditDays(UUID partyId) {
        return find(partyId).getDefaultCreditDays();
    }

    @Transactional(readOnly = true)
    public WorkerRates workerRates(UUID partyId) {
        Party party = find(partyId);
        return new WorkerRates(party.getDefaultRollingRatePerHour(), party.getDefaultWarpingRatePerKg());
    }

    private static PartyRef toRef(Party party) {
        return new PartyRef(party.getId(), party.getPartyCode(), party.getName());
    }

    // null = keep the current value; "" (or only spaces) = clear the field.
    private static String text(String requested, String current) {
        if (requested == null) {
            return current;
        }
        return requested.isBlank() ? null : requested;
    }

    Party find(UUID id) {
        return parties.findById(id).orElseThrow(() -> new ResourceNotFoundException("Party", id));
    }

    private static PartyDetails toDetails(CreatePartyRequest r) {
        return new PartyDetails(r.name(), r.addressLine(), r.area(), r.city(), r.district(), r.state(), r.pincode(),
                r.note(), r.defaultCreditDays(), r.customerCreditDays(), r.defaultRollingRatePerHour(), r.defaultWarpingRatePerKg());
    }
}
