package com.nexora.desktop.repository

import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.dto.AccountResponse
import com.nexora.desktop.api.dto.AccountsResponse
import com.nexora.desktop.api.dto.AdjustmentRequest
import com.nexora.desktop.api.dto.CashPositionResponse
import com.nexora.desktop.api.dto.NewAccountRequest
import com.nexora.desktop.api.dto.PageResponse
import com.nexora.desktop.api.dto.TransactionResponse
import com.nexora.desktop.api.dto.TransferRequest
import com.nexora.desktop.api.dto.TransferResponse
import com.nexora.desktop.viewmodel.common.ListQuery

/**
 * Accounts, transactions and transfers (05 section 10). There is no call that writes a transaction directly:
 * money moves only through business actions (FR-FIN-04).
 */
class FinanceRepository(private val api: ApiClient) {

    suspend fun accounts(): ApiResult<AccountsResponse> = api.get("/finance/accounts")

    /** Active accounts only, for the account selectors of the entry forms. */
    suspend fun activeAccounts(): ApiResult<List<AccountResponse>> =
        api.get<AccountsResponse>("/finance/accounts", mapOf("status" to "ACTIVE")).map { r -> r.accounts.filter { it.status == "ACTIVE" } }

    suspend fun cashPosition(): ApiResult<CashPositionResponse> = api.get("/finance/cash-position")

    suspend fun createAccount(request: NewAccountRequest): ApiResult<AccountResponse> = api.post("/finance/accounts", request)

    suspend fun closeAccount(id: String): ApiResult<AccountResponse> = api.postEmpty("/finance/accounts/$id/close")

    suspend fun adjust(id: String, request: AdjustmentRequest, key: String): ApiResult<TransactionResponse> =
        api.post("/finance/accounts/$id/adjustments", request, key)

    suspend fun accountTransactions(id: String, query: ListQuery): ApiResult<PageResponse<TransactionResponse>> =
        api.get("/finance/accounts/$id/transactions", query.params())

    suspend fun accountTransactionsCsv(id: String, query: ListQuery): ApiResult<ByteArray> =
        api.getBytes("/finance/accounts/$id/transactions", query.params() + ("format" to "csv"))

    suspend fun transactions(query: ListQuery): ApiResult<PageResponse<TransactionResponse>> = api.get("/finance/transactions", query.params())

    suspend fun transactionsCsv(query: ListQuery): ApiResult<ByteArray> =
        api.getBytes("/finance/transactions", query.params() + ("format" to "csv"))

    suspend fun transfer(request: TransferRequest, key: String): ApiResult<TransferResponse> = api.post("/finance/transfers", request, key)

    suspend fun importStatement(id: String, fileName: String, bytes: ByteArray): ApiResult<com.nexora.desktop.api.dto.StatementImportResponse> =
        api.upload("/finance/accounts/$id/statement-imports", {
            append("file", bytes, io.ktor.http.Headers.build {
                append(io.ktor.http.HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$fileName\"")
            })
        })
}
