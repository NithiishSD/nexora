package com.nexora.desktop.repository

import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.dto.ProfitAndLossReportLine
import com.nexora.desktop.api.dto.ProfitabilityReportLine
import com.nexora.desktop.api.dto.WorkerEfficiencyRow
import kotlinx.serialization.json.JsonElement

/**
 * Reports (05 section 16). Each report has its own shape, so the viewer reads the JSON as it comes and only formats
 * it; no total is recomputed on the client (FR-RPT-15 makes the server's totals the authority).
 */
class ReportRepository(private val api: ApiClient) {

    suspend fun load(path: String, filters: Map<String, String>): ApiResult<JsonElement> = api.get(path, filters)

    /** Same endpoint and filters with `format=csv` (06 section 3.7). */
    suspend fun csv(path: String, filters: Map<String, String>): ApiResult<ByteArray> = api.getBytes(path, filters + ("format" to "csv"))

    suspend fun workerEfficiency(from: String, to: String): ApiResult<List<WorkerEfficiencyRow>> =
        api.get("/reports/worker-efficiency", mapOf("from" to from, "to" to to))

    suspend fun profitAndLoss(from: String, to: String, groupBy: String = "MONTH"): ApiResult<List<ProfitAndLossReportLine>> =
        api.get("/reports/profit-and-loss", mapOf("from" to from, "to" to to, "groupBy" to groupBy))

    suspend fun profitability(from: String, to: String, dimension: String = "PRODUCT"): ApiResult<List<ProfitabilityReportLine>> =
        api.get("/reports/profitability", mapOf("from" to from, "to" to to, "dimension" to dimension))
}
