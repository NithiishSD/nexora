package com.nexora.desktop.viewmodel.production

import com.nexora.desktop.api.dto.BatchResponse
import com.nexora.desktop.api.dto.StartBatchRequest
import com.nexora.desktop.repository.ProductionRepository
import com.nexora.desktop.util.Dates
import com.nexora.desktop.viewmodel.common.DetailViewModel
import java.time.Clock

/** S-PRD-03 Batch detail (FR-PROD-02..-05, -08, -10). */
class BatchDetailViewModel(
    private val repository: ProductionRepository,
    private val clock: Clock,
    val batchId: String,
) : DetailViewModel<BatchResponse>(clock, { repository.get(batchId) }) {

    /**
     * Starting moves raw silk out of stock and locks the linked orders, so it is never sent directly: the screen
     * first opens the confirmation (ask("start")) and only its confirm button calls this (FR-PROD-02, rule P9).
     */
    fun start() {
        if (action.value.confirming != "start") return
        runAction({ repository.start(batchId, StartBatchRequest(Dates.today(clock).toString())) })
    }

    fun cancel(reason: String) = runAction({ repository.cancel(batchId, reason) })

    /** Buttons per status (06 S-PRD-03). */
    fun canStart(b: BatchResponse) = b.status == "PLANNED"
    fun canComplete(b: BatchResponse) = b.status == "IN_PROGRESS"
    fun canCancel(b: BatchResponse) = b.status == "PLANNED"
    fun canRecordWork(b: BatchResponse) = b.status == "IN_PROGRESS" || b.status == "COMPLETED"
}
