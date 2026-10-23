package com.nexora.desktop.repository

import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.dto.LoanPaymentRequest
import com.nexora.desktop.api.dto.LoanResponse
import com.nexora.desktop.api.dto.NewLoanRequest
import com.nexora.desktop.api.dto.PageResponse
import com.nexora.desktop.api.dto.ReasonRequest
import com.nexora.desktop.viewmodel.common.ListQuery

/** Loans (05 section 11). Money-moving calls carry an Idempotency-Key. */
class LoanRepository(private val api: ApiClient) {

    suspend fun list(query: ListQuery): ApiResult<PageResponse<LoanResponse>> = api.get("/loans", query.params())

    suspend fun get(id: String): ApiResult<LoanResponse> = api.get("/loans/$id")

    suspend fun create(request: NewLoanRequest, key: String): ApiResult<LoanResponse> = api.post("/loans", request, key)

    suspend fun repayPrincipal(id: String, request: LoanPaymentRequest, key: String): ApiResult<LoanResponse> =
        api.post("/loans/$id/principal-repayments", request, key)

    suspend fun payInterest(id: String, request: LoanPaymentRequest, key: String): ApiResult<LoanResponse> =
        api.post("/loans/$id/interest-payments", request, key)

    suspend fun reversePayment(id: String, transactionId: String, reason: String, key: String): ApiResult<LoanResponse> =
        api.post("/loans/$id/payments/$transactionId/reverse", ReasonRequest(reason), key)

    suspend fun close(id: String): ApiResult<LoanResponse> = api.postEmpty("/loans/$id/close")

    suspend fun advice(id: String): ApiResult<com.nexora.desktop.api.dto.LoanAdviceResponse> = api.get("/loans/$id/advice")
}
