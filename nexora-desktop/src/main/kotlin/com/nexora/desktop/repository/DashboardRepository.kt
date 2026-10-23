package com.nexora.desktop.repository

import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.dto.DashboardResponse

/** Owner dashboard (05 E-RPT-01). */
class DashboardRepository(private val api: ApiClient) {
    suspend fun summary(): ApiResult<DashboardResponse> = api.get("/dashboard/summary")
}
