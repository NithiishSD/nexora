package com.nexora.desktop.repository

import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.dto.BatchRequest
import com.nexora.desktop.api.dto.BatchResponse
import com.nexora.desktop.api.dto.CompleteBatchRequest
import com.nexora.desktop.api.dto.PageResponse
import com.nexora.desktop.api.dto.ReasonRequest
import com.nexora.desktop.api.dto.StartBatchRequest
import com.nexora.desktop.ui.components.PickOption
import com.nexora.desktop.util.Dates
import com.nexora.desktop.viewmodel.common.ListQuery

/** Production batches (05 section 13). */
class ProductionRepository(private val api: ApiClient) {

    suspend fun list(query: ListQuery): ApiResult<PageResponse<BatchResponse>> = api.get("/production-batches", query.params())

    suspend fun get(id: String): ApiResult<BatchResponse> = api.get("/production-batches/$id")

    suspend fun create(request: BatchRequest): ApiResult<BatchResponse> = api.post("/production-batches", request)

    suspend fun update(id: String, request: BatchRequest): ApiResult<BatchResponse> = api.patch("/production-batches/$id", request)

    suspend fun start(id: String, request: StartBatchRequest): ApiResult<BatchResponse> = api.post("/production-batches/$id/start", request)

    suspend fun complete(id: String, request: CompleteBatchRequest): ApiResult<BatchResponse> = api.post("/production-batches/$id/complete", request)

    suspend fun cancel(id: String, reason: String): ApiResult<BatchResponse> = api.post("/production-batches/$id/cancel", ReasonRequest(reason))

    /** Batches work can be recorded against (In production or Completed), for the work form's batch picker. */
    suspend fun pickForWork(text: String): ApiResult<List<PickOption>> {
        val rows = mutableListOf<BatchResponse>()
        for (status in listOf("IN_PROGRESS", "COMPLETED")) {
            when (val page = list(ListQuery(size = 50, filters = mapOf("status" to status)))) {
                is ApiResult.Success -> rows += page.value.content
                is ApiResult.Failure -> return page
            }
        }
        return ApiResult.Success(
            rows.filter { text.lowercase() in it.batchNumber.lowercase() }.map { PickOption(it.id, it.batchNumber, Dates.formatIso(it.productionDate)) },
        )
    }
}
