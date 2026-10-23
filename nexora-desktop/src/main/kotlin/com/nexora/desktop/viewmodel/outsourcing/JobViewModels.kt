package com.nexora.desktop.viewmodel.outsourcing

import com.nexora.desktop.api.dto.BatchLinkRequest
import com.nexora.desktop.api.dto.IssueRequest
import com.nexora.desktop.api.dto.JobResponse
import com.nexora.desktop.api.dto.NewJobRequest
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.OutsourcingRepository
import com.nexora.desktop.ui.components.PickOption
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.Checks
import com.nexora.desktop.viewmodel.common.DetailViewModel
import com.nexora.desktop.viewmodel.common.FormViewModel
import com.nexora.desktop.viewmodel.common.ListQuery
import com.nexora.desktop.viewmodel.common.PagedListViewModel
import com.nexora.desktop.viewmodel.common.decimal
import com.nexora.desktop.viewmodel.production.LinkRow
import java.time.Clock

/** S-OUT-01 Outsourcing jobs (FR-OUT-07). */
class JobListViewModel(repository: OutsourcingRepository, clock: Clock, filters: Map<String, String> = emptyMap()) :
    PagedListViewModel<JobResponse>(ListQuery(filters = filters), clock, { repository.list(it) })

data class JobForm(
    val manufacturer: PickOption? = null,
    val productType: String = "SILK_WARP",
    val issued: String = "",
    val rate: String = "",
    val notes: String = "",
    val links: List<LinkRow> = emptyList(),
)

/** S-OUT-02 New job (FR-OUT-01, -08). */
class NewJobViewModel(private val repository: OutsourcingRepository) : FormViewModel<JobForm, JobResponse>(JobForm()) {

    fun addLink() = edit { it.copy(links = it.links + LinkRow()) }
    fun removeLink(index: Int) = edit { f -> f.copy(links = f.links.filterIndexed { i, _ -> i != index }) }
    fun updateLink(index: Int, change: (LinkRow) -> LinkRow) = edit { f -> f.copy(links = f.links.mapIndexed { i, r -> if (i == index) change(r) else r }) }

    override fun validate(form: JobForm): Map<String, String> {
        val errors = Checks.collect(
            "manufacturerId" to if (form.manufacturer == null) s("validation.required") else null,
            "issuedWeightKg" to Checks.positive(form.issued),
            "ratePerKg" to Checks.zeroOrMore(form.rate),
        ).toMutableMap()
        form.links.forEachIndexed { i, link ->
            if (link.item == null) errors["orderItems[$i].orderItemId"] = s("validation.required")
            Checks.positive(link.weight)?.let { errors["orderItems[$i].allocatedWeightKg"] = it }
        }
        return errors
    }

    override suspend fun send(form: JobForm, idempotencyKey: String): ApiResult<JobResponse> = repository.create(
        NewJobRequest(
            form.manufacturer!!.id, form.productType, form.issued.decimal(), form.rate.decimal(), form.notes.trim().ifEmpty { null },
            form.links.map { BatchLinkRequest(it.item!!.id, it.weight.decimal()) },
        ),
    )
}

/** S-OUT-03 Job detail (FR-OUT-02..-06). */
class JobDetailViewModel(
    private val repository: OutsourcingRepository,
    private val clock: Clock,
    val jobId: String,
) : DetailViewModel<JobResponse>(clock, { repository.get(jobId) }) {

    fun canIssue(j: JobResponse) = j.status == "CREATED"
    fun canReceive(j: JobResponse) = j.status == "MATERIAL_ISSUED" || j.status == "PARTIALLY_RECEIVED"
    fun canCancel(j: JobResponse) = j.status == "CREATED"
    fun canPay(j: JobResponse) = (j.outstandingAmount?.signum() ?: 0) > 0

    /** Issue moves raw silk to the manufacturer; only called from the confirmation dialog. */
    fun issue(date: String) = runAction({ repository.issue(jobId, IssueRequest(Dates.toIso(date, clock) ?: Dates.today(clock).toString())) })

    fun cancel(reason: String) = runAction({ repository.cancel(jobId, reason) })
}
