package com.nexora.desktop.repository

import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.dto.IssueRequest
import com.nexora.desktop.api.dto.JobReceiveRequest
import com.nexora.desktop.api.dto.JobResponse
import com.nexora.desktop.api.dto.NewJobRequest
import com.nexora.desktop.api.dto.PageResponse
import com.nexora.desktop.api.dto.ReasonRequest
import com.nexora.desktop.viewmodel.common.ListQuery

/** Outsourcing jobs (05 section 14). Receiving is idempotent. */
class OutsourcingRepository(private val api: ApiClient) {

    suspend fun list(query: ListQuery): ApiResult<PageResponse<JobResponse>> = api.get("/outsourcing-jobs", query.params())

    suspend fun get(id: String): ApiResult<JobResponse> = api.get("/outsourcing-jobs/$id")

    suspend fun create(request: NewJobRequest): ApiResult<JobResponse> = api.post("/outsourcing-jobs", request)

    suspend fun issue(id: String, request: IssueRequest): ApiResult<JobResponse> = api.post("/outsourcing-jobs/$id/issue", request)

    suspend fun receive(id: String, request: JobReceiveRequest, key: String): ApiResult<JobResponse> =
        api.post("/outsourcing-jobs/$id/receive", request, key)

    suspend fun cancel(id: String, reason: String): ApiResult<JobResponse> = api.post("/outsourcing-jobs/$id/cancel", ReasonRequest(reason))
}
