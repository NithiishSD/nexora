package com.nexora.workforce.api;

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

import com.nexora.finance.domain.RecordStatus;
import com.nexora.shared.domain.PaymentStatus;
import com.nexora.workforce.api.dto.RecordWorkRequest;
import com.nexora.workforce.api.dto.ReverseWorkRecordRequest;
import com.nexora.workforce.api.dto.WorkRecordListResponse;
import com.nexora.workforce.api.dto.WorkRecordResponse;
import com.nexora.workforce.application.WorkRecordService;
import com.nexora.workforce.domain.WorkType;

import jakarta.validation.Valid;

// Work records. No PUT, PATCH or DELETE (405): a record is never edited (FR-WORK-03, FR-WORK-07).
@RestController
@RequestMapping("/api/v1/work-records")
class WorkRecordController {

    private final WorkRecordService workRecords;

    WorkRecordController(WorkRecordService workRecords) {
        this.workRecords = workRecords;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('WORK_RECORD_MANAGE')")
    ResponseEntity<WorkRecordResponse> record(@Valid @RequestBody RecordWorkRequest request) {
        WorkRecordResponse created = workRecords.record(request);
        return ResponseEntity.created(URI.create("/api/v1/work-records/" + created.id())).body(created);
    }

    // GET /api/v1/work-records?workerId=&workType=&status=&paymentStatus=&productionBatchId=&from=&to=&page=&size=&sort=
    @GetMapping
    @PreAuthorize("hasAuthority('WORK_RECORD_MANAGE')")
    WorkRecordListResponse list(
            @RequestParam(required = false) UUID workerId,
            @RequestParam(required = false) WorkType workType,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) UUID productionBatchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "workDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return workRecords.list(workerId, workType, status, paymentStatus, productionBatchId, from, to, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('WORK_RECORD_MANAGE')")
    WorkRecordResponse get(@PathVariable UUID id) {
        return workRecords.get(id);
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('WORK_RECORD_MANAGE')")
    WorkRecordResponse reverse(@PathVariable UUID id, @Valid @RequestBody ReverseWorkRecordRequest request) {
        return workRecords.reverse(id, request.reason());
    }
}
