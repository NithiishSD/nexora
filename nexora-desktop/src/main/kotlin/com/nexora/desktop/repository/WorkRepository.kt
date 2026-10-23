package com.nexora.desktop.repository

import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.dto.ReasonRequest
import com.nexora.desktop.api.dto.WorkListResponse
import com.nexora.desktop.api.dto.WorkRecordRequest
import com.nexora.desktop.api.dto.WorkRecordResponse
import com.nexora.desktop.viewmodel.common.ListQuery

/** Work records (05 section 15). */
class WorkRepository(private val api: ApiClient) {

    /** The list response carries the server's total over all matching rows next to the page. */
    suspend fun list(query: ListQuery): ApiResult<WorkListResponse> = api.get("/work-records", query.params())

    suspend fun record(request: WorkRecordRequest): ApiResult<WorkRecordResponse> = api.post("/work-records", request)

    suspend fun reverse(id: String, reason: String): ApiResult<WorkRecordResponse> = api.post("/work-records/$id/reverse", ReasonRequest(reason))
}
