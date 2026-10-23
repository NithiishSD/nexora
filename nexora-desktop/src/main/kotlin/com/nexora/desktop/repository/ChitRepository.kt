package com.nexora.desktop.repository

import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.dto.CashEntryRequest
import com.nexora.desktop.api.dto.ChitResponse
import com.nexora.desktop.api.dto.NewChitRequest
import com.nexora.desktop.api.dto.PageResponse
import com.nexora.desktop.api.dto.ReasonRequest
import com.nexora.desktop.viewmodel.common.ListQuery

/** Chits (05 section 12): every money entry is cash only and idempotent. */
class ChitRepository(private val api: ApiClient) {

    suspend fun list(query: ListQuery): ApiResult<PageResponse<ChitResponse>> = api.get("/chits", query.params())

    suspend fun get(id: String): ApiResult<ChitResponse> = api.get("/chits/$id")

    suspend fun create(request: NewChitRequest): ApiResult<ChitResponse> = api.post("/chits", request)

    /** [kind] is `contributions`, `payout` or `organiser-payments` (the three entry endpoints). */
    suspend fun entry(id: String, kind: String, request: CashEntryRequest, key: String): ApiResult<ChitResponse> =
        api.post("/chits/$id/$kind", request, key)

    suspend fun reverseEntry(id: String, transactionId: String, reason: String, key: String): ApiResult<ChitResponse> =
        api.post("/chits/$id/entries/$transactionId/reverse", ReasonRequest(reason), key)

    suspend fun close(id: String): ApiResult<ChitResponse> = api.postEmpty("/chits/$id/close")

    suspend fun calculateBid(id: String, month: String, bidAmount: com.nexora.desktop.api.Decimal): ApiResult<com.nexora.desktop.api.dto.ChitBidResponse> =
        api.post("/chits/$id/bids", com.nexora.desktop.api.dto.ChitBidRequest(month, bidAmount))
}
