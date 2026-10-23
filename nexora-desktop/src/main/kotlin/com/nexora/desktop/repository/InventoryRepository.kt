package com.nexora.desktop.repository

import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.dto.ExternalWipResponse
import com.nexora.desktop.api.dto.MovementResponse
import com.nexora.desktop.api.dto.PageResponse
import com.nexora.desktop.api.dto.StockAdjustmentRequest
import com.nexora.desktop.api.dto.StockSummaryResponse
import com.nexora.desktop.api.dto.WastageRequest
import com.nexora.desktop.viewmodel.common.ListQuery

/**
 * Inventory reads and the two stock business actions (05 section 7). There is deliberately no call that
 * creates or edits a movement: every movement comes from a business action on the server (FR-INV-01).
 */
class InventoryRepository(private val api: ApiClient) {

    suspend fun summary(): ApiResult<StockSummaryResponse> = api.get("/inventory/summary")

    suspend fun movements(query: ListQuery): ApiResult<PageResponse<MovementResponse>> = api.get("/inventory/movements", query.params())

    suspend fun movementsCsv(query: ListQuery): ApiResult<ByteArray> = api.getBytes("/inventory/movements", query.params() + ("format" to "csv"))

    suspend fun externalWip(): ApiResult<ExternalWipResponse> = api.get("/inventory/external-wip")

    /** The key is sent so a retry is safe even if the server starts requiring it (06 marks the dialog idempotent). */
    suspend fun recordWastage(request: WastageRequest, key: String): ApiResult<MovementResponse> = api.post("/inventory/wastage", request, key)

    suspend fun adjust(request: StockAdjustmentRequest, key: String): ApiResult<MovementResponse> = api.post("/inventory/adjustments", request, key)

    suspend fun getThresholds(): ApiResult<List<com.nexora.desktop.api.dto.InventoryThresholdDto>> = api.get("/inventory/thresholds")

    suspend fun setThreshold(productType: String, location: String, request: com.nexora.desktop.api.dto.SetThresholdRequest): ApiResult<com.nexora.desktop.api.dto.InventoryThresholdDto> =
        api.put("/inventory/thresholds/$productType/$location", request)

    suspend fun getLowStock(): ApiResult<List<com.nexora.desktop.api.dto.LowStockItemDto>> = api.get("/inventory/low-stock")
}
