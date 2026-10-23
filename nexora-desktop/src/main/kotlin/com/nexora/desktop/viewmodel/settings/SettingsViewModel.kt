package com.nexora.desktop.viewmodel.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.dto.AccountResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.FinanceRepository
import com.nexora.desktop.repository.SystemSettingRepository
import com.nexora.desktop.ui.components.ErrorMessages
import com.nexora.desktop.util.AppSettings
import com.nexora.desktop.util.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Properties

/** Versions shown under About (06 S-SET-01). */
data class AboutInfo(val clientVersion: String, val serverVersion: String? = null, val apiVersion: String? = null)

/** S-SET-01 Settings and about. Handles local UI settings and server-side dynamic system settings. */
class SettingsViewModel(
    private val settings: SettingsStore,
    private val api: ApiClient,
    finance: FinanceRepository,
    private val systemSettingRepo: SystemSettingRepository,
) : ViewModel() {

    val current: StateFlow<AppSettings> = settings.settings

    private val _about = MutableStateFlow(AboutInfo(clientVersion()))
    val about: StateFlow<AboutInfo> = _about.asStateFlow()

    private val _accounts = MutableStateFlow<List<AccountResponse>>(emptyList())
    val accounts: StateFlow<List<AccountResponse>> = _accounts.asStateFlow()

    private val _systemSettings = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val systemSettings: StateFlow<Map<String, Map<String, String>>> = _systemSettings.asStateFlow()

    private val _settingsStatus = MutableStateFlow<String?>(null)
    val settingsStatus: StateFlow<String?> = _settingsStatus.asStateFlow()

    init {
        viewModelScope.launch {
            val info = api.get<JsonObject>("/actuator/info").getOrNull()
            val build = (info?.get("build") as? JsonObject)?.get("version") as? JsonPrimitive
            val apiVersion = (info?.get("app") as? JsonObject)?.get("api-version") as? JsonPrimitive
            _about.value = _about.value.copy(serverVersion = build?.content, apiVersion = apiVersion?.content)
        }
        viewModelScope.launch { _accounts.value = finance.activeAccounts().getOrNull().orEmpty() }
        loadSystemSettings()
    }

    fun loadSystemSettings() {
        viewModelScope.launch {
            val res = systemSettingRepo.getAllSettings()
            if (res is ApiResult.Success) {
                _systemSettings.value = res.value
            }
        }
    }

    fun updateSystemCategory(category: String, values: Map<String, String>) {
        viewModelScope.launch {
            val res = systemSettingRepo.updateCategory(category, values)
            when (res) {
                is ApiResult.Success -> {
                    _settingsStatus.value = "$category settings saved"
                    loadSystemSettings()
                }
                is ApiResult.Failure -> {
                    val msg = ErrorMessages.detail(res.error) ?: ErrorMessages.message(res.error)
                    _settingsStatus.value = "Failed to save: $msg"
                }
            }
        }
    }

    fun triggerBackup(onCompleted: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = systemSettingRepo.exportBackup()
            when (res) {
                is ApiResult.Success -> {
                    onCompleted(true, "Backup export completed successfully (${res.value.size} bytes)")
                }
                is ApiResult.Failure -> {
                    val msg = ErrorMessages.detail(res.error) ?: ErrorMessages.message(res.error)
                    onCompleted(false, msg)
                }
            }
        }
    }

    /** Text size 100 % to 200 % in steps of 25 % (NFR-USE-06). */
    fun setTextScale(scale: Float) = settings.update { it.copy(textScale = (Math.round(scale * 4) / 4f).coerceIn(1f, 2f)) }

    fun setSidebarCollapsed(collapsed: Boolean) = settings.update { it.copy(sidebarCollapsed = collapsed) }

    /** Quick-switch to local standalone engine. */
    fun switchToLocalServer() = settings.update { it.copy(serverUrl = "http://localhost:8080") }

    /** Default account for an entry type, e.g. "expense.account" or "payment.account.CASH". */
    fun setDefault(key: String, accountId: String) = settings.remember(key, accountId)

    companion object {
        /** Version written into app.properties by the build (the same version as the installer). */
        fun clientVersion(): String = Properties().apply {
            SettingsViewModel::class.java.getResourceAsStream("/app.properties")?.use { load(it) }
        }.getProperty("version", "")
    }
}
