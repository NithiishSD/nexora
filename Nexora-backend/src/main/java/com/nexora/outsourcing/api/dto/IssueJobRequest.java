package com.nexora.outsourcing.api.dto;

import java.time.LocalDate;

import com.nexora.shared.api.BusinessDate;

// Optional body of POST /api/v1/outsourcing-jobs/{id}/issue (E-OUT-04): the issue date, default today.
public record IssueJobRequest(@BusinessDate LocalDate issueDate) {
}
