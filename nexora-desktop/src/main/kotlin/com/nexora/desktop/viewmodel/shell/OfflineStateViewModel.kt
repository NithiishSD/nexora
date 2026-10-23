package com.nexora.desktop.viewmodel.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.ApiClient
import com.nexora.desktop.api.ServerStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the shell shows about the connection (06 section 3.4). */
data class OfflineState(val status: ServerStatus = ServerStatus.ONLINE) {
    /** "Cannot reach the server" banner with Retry, shown while the last request failed to connect or timed out. */
    val showBanner: Boolean get() = status == ServerStatus.OFFLINE
}

/**
 * Offline handling (NFR-USE-04, NFR-USE-05). There are no offline writes in the MVP: when the server cannot be reached
 * the top-bar dot turns red and the banner offers Retry (= F5). Screens keep their last data with "Last updated hh:mm"
 * (list/detail view-models keep `page`/`value` on failure) and forms keep everything typed (FormViewModel).
 */
class OfflineStateViewModel(api: ApiClient) : ViewModel() {

    val state: StateFlow<OfflineState> = api.status
        .map { OfflineState(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, OfflineState(api.status.value))

    /** Retry = reload the current screen, exactly like F5. */
    fun retry(reload: () -> Unit) = reload()
}
