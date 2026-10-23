package com.nexora.desktop.repository

import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.dto.PageResponse
import com.nexora.desktop.api.dto.PaymentRequest
import com.nexora.desktop.api.dto.PaymentResponse
import com.nexora.desktop.api.dto.PaymentSummaryResponse
import com.nexora.desktop.api.dto.ReasonRequest
import com.nexora.desktop.viewmodel.common.ListQuery

/** Payments (05 section 9). Recording and reversing carry an Idempotency-Key (FR-PAY-07). */
class PaymentRepository(private val api: ApiClient) {

    suspend fun list(query: ListQuery): ApiResult<PageResponse<PaymentSummaryResponse>> = api.get("/payments", query.params())

    suspend fun csv(query: ListQuery): ApiResult<ByteArray> = api.getBytes("/payments", query.params() + ("format" to "csv"))

    suspend fun get(id: String): ApiResult<PaymentResponse> = api.get("/payments/$id")

    suspend fun record(request: PaymentRequest, key: String): ApiResult<PaymentResponse> = api.post("/payments", request, key)

    suspend fun reverse(id: String, reason: String, key: String): ApiResult<PaymentResponse> =
        api.post("/payments/$id/reverse", ReasonRequest(reason), key)

    suspend fun refund(id: String, request: com.nexora.desktop.api.dto.RefundPaymentRequest, key: String): ApiResult<PaymentResponse> =
        api.post("/payments/$id/refunds", request, key)

    suspend fun clearCheque(id: String, request: com.nexora.desktop.api.dto.ChequeActionRequest): ApiResult<PaymentResponse> =
        api.post("/payments/$id/cheque-clearance", request)

    suspend fun bounceCheque(id: String, request: com.nexora.desktop.api.dto.ChequeActionRequest): ApiResult<PaymentResponse> =
        api.post("/payments/$id/cheque-bounce", request)
}
