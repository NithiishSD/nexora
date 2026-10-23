package com.nexora.desktop.repository

import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.dto.InitializeSetupRequest
import com.nexora.desktop.api.dto.SetupStatusResponse
import com.nexora.desktop.api.dto.UpdateSettingsRequest

class SystemSettingRepository(private val api: ApiClient) {

    suspend fun getSetupStatus(serverUrl: String? = null): ApiResult<SetupStatusResponse> {
        return if (serverUrl != null) {
            api.getFrom(serverUrl, "/setup/status")
        } else {
            api.get("/setup/status")
        }
    }

    suspend fun initializeSetup(request: InitializeSetupRequest, serverUrl: String? = null): ApiResult<Map<String, String>> {
        return api.post("/setup/initialize", request, authenticated = false)
    }

    suspend fun getAllSettings(): ApiResult<Map<String, Map<String, String>>> {
        return api.get("/settings")
    }

    suspend fun updateCategory(category: String, settings: Map<String, String>): ApiResult<Map<String, String>> {
        return api.put("/settings/$category", UpdateSettingsRequest(settings))
    }

    suspend fun exportBackup(): ApiResult<ByteArray> {
        return api.getBytes("/settings/backup/export")
    }
}
