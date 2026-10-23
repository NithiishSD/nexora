package com.nexora.desktop.repository

import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.dto.AddRoleRequest
import com.nexora.desktop.api.dto.CustomerOutstandingResponse
import com.nexora.desktop.api.dto.CustomerTermsRequest
import com.nexora.desktop.api.dto.ManufacturerPayableResponse
import com.nexora.desktop.api.dto.PageResponse
import com.nexora.desktop.api.dto.PartyRequest
import com.nexora.desktop.api.dto.PartyResponse
import com.nexora.desktop.api.dto.PartySummaryResponse
import com.nexora.desktop.api.dto.StatementResponse
import com.nexora.desktop.api.dto.SupplierPayablesResponse
import com.nexora.desktop.api.dto.WorkerPayableResponse
import com.nexora.desktop.ui.components.PickOption
import com.nexora.desktop.viewmodel.common.ListQuery

/** Parties (05 section 5) and the per-party money views (05 E-PAY-06..10, E-RPT-03). */
class PartyRepository(private val api: ApiClient) {

    suspend fun list(query: ListQuery): ApiResult<PageResponse<PartySummaryResponse>> = api.get("/parties", query.params())

    /** Active parties holding [role], for pickers: code, name and primary phone. */
    suspend fun pick(text: String, role: String?): ApiResult<List<PickOption>> =
        api.get<PageResponse<PartySummaryResponse>>(
            "/parties",
            mapOf("search" to text, "role" to role, "status" to "ACTIVE", "size" to 20),
        ).map { page ->
            page.content.map { PickOption(it.id, it.name, listOfNotNull(it.partyCode, it.primaryPhone).joinToString(" · ")) }
        }

    suspend fun get(id: String): ApiResult<PartyResponse> = api.get("/parties/$id")

    suspend fun create(request: PartyRequest): ApiResult<PartyResponse> = api.post("/parties", request)

    suspend fun update(id: String, request: PartyRequest): ApiResult<PartyResponse> = api.patch("/parties/$id", request)

    suspend fun updateCustomerTerms(id: String, customerCreditDays: Int): ApiResult<PartyResponse> =
        api.patch("/parties/$id/customer-terms", CustomerTermsRequest(customerCreditDays))

    suspend fun addRole(id: String, role: String): ApiResult<PartyResponse> = api.post("/parties/$id/roles", AddRoleRequest(role))

    suspend fun deactivate(id: String): ApiResult<PartyResponse> = api.postEmpty("/parties/$id/deactivate")

    suspend fun activate(id: String): ApiResult<PartyResponse> = api.postEmpty("/parties/$id/activate")

    suspend fun customerOutstanding(id: String): ApiResult<CustomerOutstandingResponse> = api.get("/customers/$id/outstanding")

    suspend fun customerStatement(id: String, from: String, to: String): ApiResult<StatementResponse> =
        api.get("/customers/$id/statement", mapOf("from" to from, "to" to to))

    suspend fun customerStatementCsv(id: String, from: String, to: String): ApiResult<ByteArray> =
        api.getBytes("/customers/$id/statement", mapOf("from" to from, "to" to to, "format" to "csv"))

    suspend fun supplierPayables(id: String): ApiResult<SupplierPayablesResponse> = api.get("/suppliers/$id/payables")

    suspend fun manufacturerPayable(id: String): ApiResult<ManufacturerPayableResponse> = api.get("/manufacturers/$id/payable")

    suspend fun workerPayable(id: String, from: String?, to: String?): ApiResult<WorkerPayableResponse> =
        api.get("/workers/$id/payable", mapOf("from" to from, "to" to to))
}
