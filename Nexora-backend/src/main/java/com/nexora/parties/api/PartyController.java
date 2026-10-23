package com.nexora.parties.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.parties.api.dto.AddRoleRequest;
import com.nexora.parties.api.dto.CreatePartyRequest;
import com.nexora.parties.api.dto.UpdatePartyRequest;
import com.nexora.parties.api.dto.PartyResponse;
import com.nexora.parties.api.dto.PartySummaryResponse;
import com.nexora.parties.application.PartyService;
import com.nexora.parties.domain.PartyRole;
import com.nexora.parties.domain.PartyStatus;
import com.nexora.shared.api.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

// HTTP only: validate, call one service method, choose the status. No business rules here.
// There is deliberately no DELETE mapping: parties are never deleted (FR-PARTY-15 -> 405).
@RestController
@RequestMapping("/api/v1/parties")
class PartyController {

    private final PartyService partyService;

    PartyController(PartyService partyService) {
        this.partyService = partyService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PARTY_MANAGE')")
    ResponseEntity<PartyResponse> create(@Valid @RequestBody CreatePartyRequest request) {
        PartyResponse created = partyService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/parties/" + created.id())).body(created);   // 201 + Location
    }

    // GET /api/v1/parties?role=&status=&search=&page=&size=&sort=  (size capped at 100 by configuration)
    @GetMapping
    @PreAuthorize("hasAuthority('PARTY_VIEW')")
    PageResponse<PartySummaryResponse> list(
            @RequestParam(required = false) PartyRole role,          // unknown value -> 400 MALFORMED_REQUEST
            @RequestParam(required = false) PartyStatus status,
            @RequestParam(required = false) @Size(max = 100) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return partyService.list(role, status, search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PARTY_VIEW')")
    PartyResponse get(@PathVariable UUID id) {          // a malformed UUID is 400 MALFORMED_REQUEST
        return partyService.get(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PARTY_MANAGE')")
    PartyResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePartyRequest request) {
        return partyService.update(id, request);
    }

    // Business actions are sub-resources (ADR-012).
    @PostMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('PARTY_MANAGE')")
    PartyResponse addRole(@PathVariable UUID id, @Valid @RequestBody AddRoleRequest request) {
        return partyService.addRole(id, request.role());
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PARTY_MANAGE')")
    PartyResponse deactivate(@PathVariable UUID id) {
        return partyService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PARTY_MANAGE')")
    PartyResponse activate(@PathVariable UUID id) {
        return partyService.activate(id);
    }

    @PatchMapping("/{id}/customer-terms")
    @PreAuthorize("hasAuthority('PARTY_MANAGE')")
    PartyResponse updateCustomerTerms(@PathVariable UUID id, @Valid @RequestBody com.nexora.parties.api.dto.CustomerTermsRequest request) {
        return partyService.updateCustomerTerms(id, request.creditPeriodDays());
    }
}
