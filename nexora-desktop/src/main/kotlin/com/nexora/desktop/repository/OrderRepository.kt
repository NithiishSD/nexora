package com.nexora.desktop.repository

import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.dto.DeliverRequest
import com.nexora.desktop.api.dto.OrderRequest
import com.nexora.desktop.api.dto.OrderResponse
import com.nexora.desktop.api.dto.OrderSummaryResponse
import com.nexora.desktop.api.dto.PageResponse
import com.nexora.desktop.api.dto.ReasonRequest
import com.nexora.desktop.viewmodel.common.ListQuery
import com.nexora.desktop.ui.components.PickOption
import com.nexora.desktop.util.IndianFormat
import com.nexora.desktop.util.s

/** Customer orders and their status actions (05 section 8). Delivery is idempotent. */
class OrderRepository(private val api: ApiClient) {

    suspend fun list(query: ListQuery): ApiResult<PageResponse<OrderSummaryResponse>> = api.get("/orders", query.params())

    suspend fun csv(query: ListQuery): ApiResult<ByteArray> = api.getBytes("/orders", query.params() + ("format" to "csv"))

    suspend fun get(id: String): ApiResult<OrderResponse> = api.get("/orders/$id")

    suspend fun create(request: OrderRequest): ApiResult<OrderResponse> = api.post("/orders", request)

    suspend fun update(id: String, request: OrderRequest): ApiResult<OrderResponse> = api.patch("/orders/$id", request)

    suspend fun confirm(id: String): ApiResult<OrderResponse> = api.postEmpty("/orders/$id/confirm")

    suspend fun ready(id: String): ApiResult<OrderResponse> = api.postEmpty("/orders/$id/ready")

    suspend fun deliver(id: String, request: DeliverRequest, key: String): ApiResult<OrderResponse> = api.post("/orders/$id/deliver", request, key)

    suspend fun deliverItem(orderId: String, itemId: String, request: com.nexora.desktop.api.dto.DeliverItemRequest, key: String): ApiResult<OrderResponse> =
        api.post("/orders/$orderId/items/$itemId/deliveries", request, key)

    suspend fun generateBill(orderId: String): ApiResult<com.nexora.desktop.api.dto.DocumentResponse> =
        api.postEmpty("/orders/$orderId/bill")

    suspend fun complete(id: String): ApiResult<OrderResponse> = api.postEmpty("/orders/$id/complete")

    suspend fun cancel(id: String, reason: String): ApiResult<OrderResponse> = api.post("/orders/$id/cancel", ReasonRequest(reason))

    suspend fun recordReturn(orderId: String, request: com.nexora.desktop.api.dto.RecordCustomerReturnRequest, key: String): ApiResult<com.nexora.desktop.api.dto.CustomerReturnResponse> =
        api.post("/orders/$orderId/returns", request, key)

    suspend fun getReturns(orderId: String): ApiResult<List<com.nexora.desktop.api.dto.CustomerReturnResponse>> =
        api.get("/orders/$orderId/returns")

    /**
     * Items of confirmed orders with the given product, for linking a batch or job to orders (06 S-PRD-02).
     * The order list has no text search, so the typed text narrows by order number or customer on the client.
     */
    suspend fun pickItems(productType: String, text: String): ApiResult<List<PickOption>> {
        val page = list(ListQuery(size = 50, filters = mapOf("status" to "CONFIRMED", "productType" to productType)))
        val orders = (page as? ApiResult.Success)?.value?.content ?: return page as ApiResult.Failure
        val needle = text.lowercase()
        val options = orders.filter { needle in it.orderNumber.lowercase() || needle in it.customer.name.lowercase() }.flatMap { summary ->
            get(summary.id).getOrNull()?.items.orEmpty().filter { it.productType == productType }.map {
                PickOption(it.id, s("payment.orderLine", summary.orderNumber, it.lineNo), "${summary.customer.name} · ${IndianFormat.weight(it.requiredWeightKg)}")
            }
        }
        return ApiResult.Success(options)
    }
}
