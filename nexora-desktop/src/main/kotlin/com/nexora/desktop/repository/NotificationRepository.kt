package com.nexora.desktop.repository

import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.dto.NotificationConfig
import com.nexora.desktop.api.dto.NotificationConfigRequest
import com.nexora.desktop.api.dto.NotificationResponse
import com.nexora.desktop.api.dto.PageResponse
import com.nexora.desktop.viewmodel.common.ListQuery

/** Reminders (05 section 17): read-only list in the MVP, plus the reminder settings. */
class NotificationRepository(private val api: ApiClient) {

    suspend fun list(query: ListQuery): ApiResult<PageResponse<NotificationResponse>> = api.get("/notifications", query.params())

    /** Number of PENDING reminders: the server's totalElements of a one-row page. */
    suspend fun pendingCount(): ApiResult<Long> =
        api.get<PageResponse<NotificationResponse>>("/notifications", mapOf("status" to "PENDING", "size" to 1)).map { it.totalElements }

    suspend fun configurations(): ApiResult<List<NotificationConfig>> = api.get("/notifications/configurations")

    suspend fun updateConfiguration(type: String, request: NotificationConfigRequest): ApiResult<NotificationConfig> =
        api.put("/notifications/configurations/$type", request)

    suspend fun retry(id: String): ApiResult<NotificationResponse> =
        api.postEmpty("/notifications/$id/retry")
}
