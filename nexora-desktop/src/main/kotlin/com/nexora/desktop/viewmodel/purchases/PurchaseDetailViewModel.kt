package com.nexora.desktop.viewmodel.purchases

import com.nexora.desktop.api.dto.PurchaseNotesRequest
import com.nexora.desktop.api.dto.PurchaseResponse
import com.nexora.desktop.repository.PurchaseRepository
import com.nexora.desktop.viewmodel.common.DetailViewModel
import java.time.Clock

/** Which purchase actions are offered right now (06 S-PUR-03 button table). */
data class PurchaseActions(
    val receive: Boolean,
    val returnToSupplier: Boolean,
    val cancel: Boolean,
    val pay: Boolean,
)

/** S-PUR-03 Purchase detail (FR-PROC-05, -06, -11, -12, -15, -16). */
class PurchaseDetailViewModel(
    private val repository: PurchaseRepository,
    clock: Clock,
    val purchaseId: String,
) : DetailViewModel<PurchaseResponse>(clock, { repository.get(purchaseId) }) {

    /**
     * Buttons follow the status and figures from the API. These comparisons only decide whether a button is
     * shown; they produce no figure that is displayed (NFR-USE-02).
     */
    fun actionsFor(p: PurchaseResponse): PurchaseActions = PurchaseActions(
        receive = p.status == "ORDERED" || p.status == "PARTIALLY_RECEIVED",
        returnToSupplier = p.status != "CANCELLED" && p.acceptedWeightKg > p.returnedWeightKg,
        // Only an untouched purchase can be cancelled: nothing received and nothing paid (FR-PROC-12).
        cancel = p.status == "ORDERED" && p.receivedWeightKg.signum() == 0 && p.allocatedAmount.signum() == 0,
        pay = p.outstandingAmount.signum() > 0,
    )

    fun cancel(reason: String) = runAction({ repository.cancel(purchaseId, reason) })

    fun saveNotes(notes: String, billReference: String) = runAction({
        repository.editNotes(purchaseId, PurchaseNotesRequest(notes.trim().ifEmpty { null }, billReference.trim().ifEmpty { null }))
    })
}
