package com.nexora.desktop.repository

import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.dto.AllSupplierPayablesResponse
import com.nexora.desktop.api.dto.NewPurchaseRequest
import com.nexora.desktop.api.dto.PageResponse
import com.nexora.desktop.api.dto.PurchaseNotesRequest
import com.nexora.desktop.api.dto.PurchaseResponse
import com.nexora.desktop.api.dto.PurchaseSummaryResponse
import com.nexora.desktop.api.dto.ReasonRequest
import com.nexora.desktop.api.dto.ReceiptRequest
import com.nexora.desktop.api.dto.ReturnRequest
import com.nexora.desktop.ui.components.PickOption
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.IndianFormat
import com.nexora.desktop.viewmodel.common.ListQuery

/** Purchases, receipts and supplier returns (05 section 6). Receipts and returns are idempotent. */
class PurchaseRepository(private val api: ApiClient) {

    suspend fun list(query: ListQuery): ApiResult<PageResponse<PurchaseSummaryResponse>> = api.get("/purchases", query.params())

    suspend fun csv(query: ListQuery): ApiResult<ByteArray> = api.getBytes("/purchases", query.params() + ("format" to "csv"))

    suspend fun get(id: String): ApiResult<PurchaseResponse> = api.get("/purchases/$id")

    suspend fun create(request: NewPurchaseRequest): ApiResult<PurchaseResponse> = api.post("/purchases", request)

    suspend fun editNotes(id: String, request: PurchaseNotesRequest): ApiResult<PurchaseResponse> = api.patch("/purchases/$id", request)

    suspend fun cancel(id: String, reason: String): ApiResult<PurchaseResponse> = api.post("/purchases/$id/cancel", ReasonRequest(reason))

    suspend fun receive(id: String, request: ReceiptRequest, key: String): ApiResult<PurchaseResponse> =
        api.post("/purchases/$id/receipts", request, key)

    suspend fun returnToSupplier(id: String, request: ReturnRequest, key: String): ApiResult<PurchaseResponse> =
        api.post("/purchases/$id/returns", request, key)

    suspend fun supplierPayables(): ApiResult<AllSupplierPayablesResponse> = api.get("/payables/suppliers")

    /**
     * Purchases that can still receive silk (Ordered or Partly received), for the Ctrl+Shift+R picker.
     * The list endpoint has no text search, so the typed text only narrows the rows already returned.
     */
    suspend fun pickOpen(text: String): ApiResult<List<PickOption>> {
        val rows = mutableListOf<PurchaseSummaryResponse>()
        for (status in listOf("ORDERED", "PARTIALLY_RECEIVED")) {
            when (val page = list(ListQuery(size = 100, filters = mapOf("status" to status)))) {
                is ApiResult.Success -> rows += page.value.content
                is ApiResult.Failure -> return page
            }
        }
        val needle = text.lowercase()
        return ApiResult.Success(
            rows.filter { needle in it.purchaseNumber.lowercase() || needle in it.supplier.name.lowercase() }.map {
                PickOption(it.id, "${it.purchaseNumber} · ${it.supplier.name}", "${Dates.formatIso(it.purchaseDate)} · ${IndianFormat.weight(it.orderedWeightKg)}")
            },
        )
    }
}
