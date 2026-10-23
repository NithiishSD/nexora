package com.nexora.desktop.viewmodel.dashboard

import com.nexora.desktop.api.dto.DashboardResponse
import com.nexora.desktop.repository.DashboardRepository
import com.nexora.desktop.viewmodel.common.DetailViewModel
import java.time.Clock

/**
 * S-DASH-01. Holds the server's dashboard exactly as returned: money available, obligations and stock are
 * three separate groups and nothing is added, subtracted or derived here (FR-RPT-02, NFR-USE-02).
 */
class DashboardViewModel(repository: DashboardRepository, clock: Clock) :
    DetailViewModel<DashboardResponse>(clock, { repository.summary() }) {

    companion object {
        /** Auto refresh while the dashboard is visible (06 S-DASH-01). */
        const val REFRESH_MILLIS = 5 * 60 * 1000L
    }
}
