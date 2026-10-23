package com.nexora.desktop.viewmodel.payments

import androidx.lifecycle.viewModelScope
import com.nexora.desktop.api.dto.AccountResponse
import com.nexora.desktop.api.dto.AllocationRequest
import com.nexora.desktop.api.dto.PaymentRequest
import com.nexora.desktop.api.dto.PaymentResponse
import com.nexora.desktop.repository.ApiResult
import com.nexora.desktop.repository.FinanceRepository
import com.nexora.desktop.repository.OrderRepository
import com.nexora.desktop.repository.PartyRepository
import com.nexora.desktop.repository.PaymentRepository
import com.nexora.desktop.ui.components.PickOption
import com.nexora.desktop.ui.components.toDecimalOrNull
import com.nexora.desktop.util.Dates
import com.nexora.desktop.util.SettingsStore
import com.nexora.desktop.util.s
import com.nexora.desktop.viewmodel.common.AccountRules
import com.nexora.desktop.viewmodel.common.Checks
import com.nexora.desktop.viewmodel.common.FormViewModel
import com.nexora.desktop.viewmodel.common.decimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock

/** One obligation of the party that a payment can be allocated to, with the server's outstanding figure. */
data class OpenItem(val targetType: String, val targetId: String, val label: String, val date: String, val outstanding: BigDecimal)

data class PaymentForm(
    /** CUSTOMER = money received; SUPPLIER / MANUFACTURER / WORKER = money paid. */
    val purpose: String = "CUSTOMER",
    val party: PickOption? = null,
    val amount: String = "",
    val method: String? = null,
    val accountId: String? = null,
    val paymentDate: String = "",
    val referenceNumber: String = "",
    val notes: String = "",
    val manualAllocation: Boolean = false,
    /** Target id -> typed amount, used only with manual allocation. */
    val allocations: Map<String, String> = emptyMap(),
)

/**
 * S-PAY-02 Record payment (FR-PAY-01..-04, -07..-13, NFR-USE-01). Default path = party, amount, method, account,
 * date, with method, account and date already filled in. Allocation is the server's: with manual allocation off
 * nothing is sent and the result view shows what the server allocated (NFR-USE-02).
 */
class RecordPaymentViewModel(
    private val payments: PaymentRepository,
    private val parties: PartyRepository,
    private val orders: OrderRepository,
    private val finance: FinanceRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
    prefillPartyId: String? = null,
    prefillPurpose: String? = null,
) : FormViewModel<PaymentForm, PaymentResponse>(PaymentForm()) {

    private val _accounts = MutableStateFlow<List<AccountResponse>>(emptyList())
    val accounts: StateFlow<List<AccountResponse>> = _accounts.asStateFlow()

    private val _openItems = MutableStateFlow<List<OpenItem>>(emptyList())

    /** The party's open items from the API, oldest first, shown as information (06 S-PAY-02). */
    val openItems: StateFlow<List<OpenItem>> = _openItems.asStateFlow()

    init {
        preset { defaults(it.copy(purpose = prefillPurpose ?: "CUSTOMER")) }
        viewModelScope.launch {
            _accounts.value = finance.activeAccounts().getOrNull().orEmpty()
            preset { it.copy(accountId = AccountRules.defaultAccount(offeredAccounts(it.method), settings.lastUsed("payment.account.${it.method}"))) }
            if (prefillPartyId != null) {
                parties.get(prefillPartyId).getOrNull()?.let { p -> preset { it.copy(party = PickOption(p.id, p.name, p.partyCode)) } }
                loadOpenItems()
            }
        }
    }

    /** Today and the method last used on this computer (06 rule P3). */
    private fun defaults(form: PaymentForm) = form.copy(
        paymentDate = Dates.format(Dates.today(clock)),
        method = settings.lastUsed("payment.method"),
    )

    /** Accounts offered for a method: Cash method -> Cash accounts only, others -> Bank accounts (FR-PAY-12). */
    fun offeredAccounts(method: String?): List<AccountResponse> = AccountRules.accountsFor(method, _accounts.value)

    fun setPurpose(purpose: String) {
        edit { it.copy(purpose = purpose, party = null, allocations = emptyMap()) }
        _openItems.value = emptyList()
    }

    fun pickParty(option: PickOption) {
        edit { it.copy(party = option, allocations = emptyMap()) }
        viewModelScope.launch { loadOpenItems() }
    }

    fun setMethod(method: String) = edit {
        // Keep the account only if it suits the new method; otherwise offer the last one used for that method.
        val offered = offeredAccounts(method)
        val keep = it.accountId?.takeIf { id -> offered.any { a -> a.id == id } }
        it.copy(method = method, accountId = keep ?: AccountRules.defaultAccount(offered, settings.lastUsed("payment.account.$method")))
    }

    fun setAllocation(targetId: String, amount: String) = edit { it.copy(allocations = it.allocations + (targetId to amount)) }

    private suspend fun loadOpenItems() {
        val form = form.value
        val partyId = form.party?.id ?: return
        _openItems.value = when (form.purpose) {
            "CUSTOMER" -> customerItems(partyId)
            "SUPPLIER" -> parties.supplierPayables(partyId).getOrNull()?.let { p ->
                p.purchases.filter { it.outstanding.signum() > 0 }.map { OpenItem("PURCHASE", it.purchaseId, it.purchaseNumber, it.purchaseDate, it.outstanding) } +
                    p.openingObligations.toItems()
            }.orEmpty()
            "MANUFACTURER" -> parties.manufacturerPayable(partyId).getOrNull()?.let { p ->
                p.jobs.filter { it.outstanding.signum() > 0 }.map { OpenItem("OUTSOURCING_JOB", it.jobId, it.jobNumber, "", it.outstanding) } +
                    p.openingObligations.toItems()
            }.orEmpty()
            "WORKER" -> parties.workerPayable(partyId, null, null).getOrNull()?.let { p ->
                p.records.filter { it.outstanding.signum() > 0 }.map {
                    OpenItem("WORK_RECORD", it.workRecordId, s("payment.workLine", Dates.formatIso(it.workDate)), it.workDate, it.outstanding)
                } + p.openingObligations.toItems()
            }.orEmpty()
            else -> emptyList()
        }
    }

    /**
     * Customer allocations target order ITEMS, while the outstanding endpoint lists orders; the items of each open
     * order are read from the order itself (05 E-ORD-03) with their server outstanding.
     */
    private suspend fun customerItems(partyId: String): List<OpenItem> {
        val outstanding = parties.customerOutstanding(partyId).getOrNull() ?: return emptyList()
        val items = outstanding.orders.filter { it.outstanding.signum() > 0 }.flatMap { o ->
            orders.get(o.orderId).getOrNull()?.items.orEmpty()
                .filter { (it.outstandingAmount ?: BigDecimal.ZERO).signum() > 0 }
                .map { OpenItem("ORDER_ITEM", it.id, s("payment.orderLine", o.orderNumber, it.lineNo), o.orderDate, it.outstandingAmount!!) }
        }
        return items + outstanding.openingObligations.toItems()
    }

    private fun List<com.nexora.desktop.api.dto.OpeningObligationLine>.toItems() =
        filter { it.outstanding.signum() > 0 }.map { OpenItem("OPENING_OBLIGATION", it.id, s("party.openingBalance"), it.obligationDate, it.outstanding) }

    override fun validate(form: PaymentForm): Map<String, String> {
        val errors = Checks.collect(
            "partyId" to if (form.party == null) s("validation.required") else null,
            "amount" to Checks.positive(form.amount),
            "paymentMethod" to if (form.method == null) s("validation.required") else null,
            "financialAccountId" to if (form.accountId == null) s("validation.required") else null,
            "paymentDate" to Checks.date(form.paymentDate, clock),
        ).toMutableMap()
        if (form.manualAllocation) {
            // Input checks only: each amount within that item's outstanding (from the API) and the entered amounts
            // not above the payment amount. What the server finally allocates is shown after saving.
            _openItems.value.forEach { item ->
                val typed = form.allocations[item.targetId]?.toDecimalOrNull() ?: return@forEach
                if (typed > item.outstanding) errors["allocations.${item.targetId}"] = s("payment.aboveOutstanding")
            }
            val entered = form.allocations.values.mapNotNull { it.toDecimalOrNull() }.fold(BigDecimal.ZERO, BigDecimal::add)
            val amount = form.amount.toDecimalOrNull()
            if (amount != null && entered > amount) errors["allocations"] = s("payment.allocationsAboveAmount")
        }
        return errors
    }

    override suspend fun send(form: PaymentForm, idempotencyKey: String): ApiResult<PaymentResponse> {
        val allocations = if (!form.manualAllocation) null else _openItems.value.mapNotNull { item ->
            form.allocations[item.targetId]?.toDecimalOrNull()?.takeIf { it.signum() > 0 }?.let { AllocationRequest(item.targetType, item.targetId, it) }
        }
        return payments.record(
            PaymentRequest(
                purpose = form.purpose,
                partyId = form.party!!.id,
                amount = form.amount.decimal(),
                paymentMethod = form.method!!,
                financialAccountId = form.accountId!!,
                paymentDate = Dates.toIso(form.paymentDate, clock)!!,
                referenceNumber = form.referenceNumber.trim().ifEmpty { null },
                notes = form.notes.trim().ifEmpty { null },
                allocations = allocations,
            ),
            idempotencyKey,
        )
    }

    override fun afterSuccess(result: PaymentResponse) {
        // Remembered on this computer for the next entry (06 rule P3).
        settings.remember("payment.method", result.paymentMethod)
        settings.remember("payment.account.${result.paymentMethod}", result.account.id)
    }

    /** "Record another": same direction, fresh entry with the defaults. */
    fun recordAnother() {
        clearResult()
        preset { f -> defaults(PaymentForm(purpose = f.purpose)).let { it.copy(accountId = AccountRules.defaultAccount(offeredAccounts(it.method), settings.lastUsed("payment.account.${it.method}"))) } }
        _openItems.value = emptyList()
    }
}
