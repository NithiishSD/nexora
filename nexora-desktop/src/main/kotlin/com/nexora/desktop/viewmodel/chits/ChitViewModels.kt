package com.nexora.desktop.viewmodel.chits

import com.nexora.desktop.api.dto.ChitResponse
import com.nexora.desktop.api.dto.NewChitRequest
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.ChitRepository
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.Checks
import com.nexora.desktop.viewmodel.common.FormViewModel
import com.nexora.desktop.viewmodel.common.ListQuery
import com.nexora.desktop.viewmodel.common.PagedListViewModel
import com.nexora.desktop.viewmodel.common.decimal
import java.time.Clock

/** S-CHT-01 Chits list (FR-CHIT-05). */
class ChitListViewModel(repository: ChitRepository, clock: Clock) :
    PagedListViewModel<ChitResponse>(ListQuery(), clock, { repository.list(it) })

data class ChitForm(
    val name: String = "",
    val total: String = "",
    val monthly: String = "",
    val months: String = "",
    val startDate: String = "",
    val notes: String = "",
)

/** S-CHT-02 New chit (FR-CHIT-01). Amounts are entered by the owner; nothing is calculated (Q6). */
class NewChitViewModel(private val chits: ChitRepository, private val clock: Clock) :
    FormViewModel<ChitForm, ChitResponse>(ChitForm(startDate = Dates.format(Dates.today(clock)))) {

    override fun validate(form: ChitForm): Map<String, String> = Checks.collect(
        "chitName" to Checks.required(form.name),
        "totalAmount" to Checks.positive(form.total),
        "monthlyAmount" to Checks.positive(form.monthly),
        "durationMonths" to if ((form.months.toIntOrNull() ?: 0) !in 1..120) s("validation.range", 1, 120) else null,
        "startDate" to Checks.date(form.startDate, clock, notAfterToday = false),
    )

    override suspend fun send(form: ChitForm, idempotencyKey: String): ApiResult<ChitResponse> = chits.create(
        NewChitRequest(
            form.name.trim(), form.total.decimal(), form.monthly.decimal(), form.months.toInt(),
            Dates.toIso(form.startDate, clock)!!, form.notes.trim().ifEmpty { null },
        ),
    )
}
