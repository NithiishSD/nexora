package com.nexora.outsourcing.api;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.outsourcing.api.dto.CancelJobRequest;
import com.nexora.outsourcing.api.dto.CreateJobRequest;
import com.nexora.outsourcing.api.dto.IssueJobRequest;
import com.nexora.outsourcing.api.dto.JobResponse;
import com.nexora.outsourcing.api.dto.ReceiveJobRequest;
import com.nexora.outsourcing.application.OutsourcingService;
import com.nexora.outsourcing.domain.JobStatus;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.idempotency.Idempotent;

import jakarta.validation.Valid;

// Outsourcing jobs. No PUT, PATCH or DELETE (405): amounts and weights change only through issue and receipts.
@RestController
@RequestMapping("/api/v1/outsourcing-jobs")
class OutsourcingJobController {

    private final OutsourcingService outsourcingService;

    OutsourcingJobController(OutsourcingService outsourcingService) {
        this.outsourcingService = outsourcingService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('OUTSOURCING_MANAGE')")
    ResponseEntity<JobResponse> create(@Valid @RequestBody CreateJobRequest request) {
        JobResponse created = outsourcingService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/outsourcing-jobs/" + created.id())).body(created);
    }

    // GET /api/v1/outsourcing-jobs?manufacturerId=&status=&orderId=&from=&to=&page=&size=&sort=
    @GetMapping
    @PreAuthorize("hasAuthority('OUTSOURCING_MANAGE')")
    PageResponse<JobResponse> list(
            @RequestParam(required = false) UUID manufacturerId,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return outsourcingService.list(manufacturerId, status, orderId, from, to, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('OUTSOURCING_MANAGE')")
    JobResponse get(@PathVariable UUID id) {
        return outsourcingService.get(id);
    }

    // No idempotency key: a repeat is refused by the state machine (409), never applied twice.
    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAuthority('OUTSOURCING_MANAGE')")
    JobResponse issue(@PathVariable UUID id, @Valid @RequestBody(required = false) IssueJobRequest request) {
        return outsourcingService.issue(id, request);
    }

    // A receipt moves stock: a retried request with the same key is answered from the first one (FR-OUT-09).
    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('OUTSOURCING_MANAGE')")
    @Idempotent
    JobResponse receive(@PathVariable UUID id, @Valid @RequestBody ReceiveJobRequest request) {
        return outsourcingService.receive(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('OUTSOURCING_MANAGE')")
    JobResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelJobRequest request) {
        return outsourcingService.cancel(id, request.reason());
    }
}
