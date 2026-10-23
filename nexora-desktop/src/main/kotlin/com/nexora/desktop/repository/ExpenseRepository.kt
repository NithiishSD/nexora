package com.nexora.desktop.repository

import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.dto.ExpenseRequest
import com.nexora.desktop.api.dto.ExpenseResponse
import com.nexora.desktop.api.dto.ExpenseSummaryResponse
import com.nexora.desktop.api.dto.PageResponse
import com.nexora.desktop.api.dto.ReasonRequest
import com.nexora.desktop.viewmodel.common.ListQuery

/** Expenses and personal drawings (05 E-FIN-12..16). Recording and reversing are idempotent. */
class ExpenseRepository(private val api: ApiClient) {

    suspend fun list(query: ListQuery): ApiResult<PageResponse<ExpenseResponse>> = api.get("/expenses", query.params())

    suspend fun csv(query: ListQuery): ApiResult<ByteArray> = api.getBytes("/expenses", query.params() + ("format" to "csv"))

    suspend fun summary(from: String, to: String): ApiResult<ExpenseSummaryResponse> = api.get("/expenses/summary", mapOf("from" to from, "to" to to))

    suspend fun record(request: ExpenseRequest, key: String): ApiResult<ExpenseResponse> = api.post("/expenses", request, key)

    suspend fun reverse(id: String, reason: String, key: String): ApiResult<ExpenseResponse> = api.post("/expenses/$id/reverse", ReasonRequest(reason), key)

    suspend fun categories(): ApiResult<List<com.nexora.desktop.api.dto.ExpenseCategoryResponse>> = api.get("/expense-categories")

    suspend fun createCategory(name: String): ApiResult<com.nexora.desktop.api.dto.ExpenseCategoryResponse> =
        api.post("/expense-categories", com.nexora.desktop.api.dto.CreateExpenseCategoryRequest(name))
}
