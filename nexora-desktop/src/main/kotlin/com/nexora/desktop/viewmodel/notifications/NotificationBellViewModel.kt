package com.nexora.desktop.viewmodel.notifications

import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.dto.NotificationConfig
import com.nexora.desktop.api.dto.NotificationConfigRequest
import com.nexora.desktop.api.dto.NotificationResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.NotificationRepository
import com.nexora.desktop.viewmodel.common.ListQuery
import com.nexora.desktop.viewmodel.common.PagedListViewModel
import androidx.lifecycle.ViewModel
import com.nexora.desktop.api.ApiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * The top-bar bell (FR-NTF-05): the number of PENDING reminders as counted by the server. The shell refreshes it
 * every 5 minutes.
 */
class NotificationBellViewModel(private val repository: NotificationRepository) : ViewModel() {

    private val _count = MutableStateFlow<Long?>(null)
    val count: StateFlow<Long?> = _count.asStateFlow()

    fun refresh() {
        viewModelScope.launch { repository.pendingCount().getOrNull()?.let { _count.value = it } }
    }

    companion object {
        const val REFRESH_MILLIS = 5 * 60 * 1000L
    }
}

/** S-NTF-01 Notifications list (read-only in the MVP, retry in M19). */
class NotificationListViewModel(private val repository: NotificationRepository, clock: Clock) :
    PagedListViewModel<NotificationResponse>(ListQuery(), clock, { repository.list(it) }) {

    fun retry(id: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.retry(id)
            load()
            onDone()
        }
    }
}

data class ReminderSettingsState(
    val configs: List<NotificationConfig> = emptyList(),
    val saving: String? = null,
    val error: ApiError? = null,
    val saved: String? = null,
)

/** S-NTF-02 Reminder settings (FR-NTF-01): days before due 0-30, repeat every 1-30 days while overdue. */
class ReminderSettingsViewModel(private val repository: NotificationRepository) : ViewModel() {

    private val _state = MutableStateFlow(ReminderSettingsState())
    val state: StateFlow<ReminderSettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = repository.configurations()) {
                is ApiResult.Success -> _state.update { it.copy(configs = r.value) }
                is ApiResult.Failure -> _state.update { it.copy(error = r.error) }
            }
        }
    }

    fun change(type: String, change: (NotificationConfig) -> NotificationConfig) =
        _state.update { s -> s.copy(configs = s.configs.map { if (it.notificationType == type) change(it) else it }, saved = null) }

    fun save(type: String) {
        val c = _state.value.configs.firstOrNull { it.notificationType == type } ?: return
        _state.update { it.copy(saving = type, error = null) }
        viewModelScope.launch {
            val request = NotificationConfigRequest(c.enabled, c.daysBeforeDue.coerceIn(0, 30), c.overdueRepeatDays.coerceIn(1, 30), c.priority)
            when (val r = repository.updateConfiguration(type, request)) {
                is ApiResult.Success -> _state.update { s ->
                    s.copy(configs = s.configs.map { if (it.notificationType == type) r.value else it }, saving = null, saved = type)
                }
                is ApiResult.Failure -> _state.update { it.copy(saving = null, error = r.error) }
            }
        }
    }
}
