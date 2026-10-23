package com.nexora.desktop.viewmodel.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.nexora.desktop.api.dto.MeResponse
import com.nexora.desktop.util.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Sidebar entries in the order of 06 section 7, each with the permission needed to see it (null = always). */
enum class Section(val labelKey: String, val permission: String?) {
    DASHBOARD("nav.dashboard", "REPORT_VIEW"),
    PARTIES("nav.parties", "PARTY_VIEW"),
    PURCHASES("nav.purchases", "PURCHASE_VIEW"),
    INVENTORY("nav.inventory", "INVENTORY_VIEW"),
    ORDERS("nav.orders", "ORDER_VIEW"),
    PAYMENTS("nav.payments", "PAYMENT_VIEW"),
    PRODUCTION("nav.production", "PRODUCTION_VIEW"),
    OUTSOURCING("nav.outsourcing", "OUTSOURCING_MANAGE"),
    WORK("nav.work", "WORK_RECORD_MANAGE"),
    MONEY("nav.money", "FINANCE_VIEW"),
    EXPENSES("nav.expenses", "EXPENSE_CREATE"),
    LOANS("nav.loans", "LOAN_MANAGE"),
    CHITS("nav.chits", "CHIT_MANAGE"),
    REPORTS("nav.reports", "REPORT_VIEW"),
    DOCUMENTS("nav.documents", "DOCUMENT_MANAGE"),
    SETTINGS("nav.settings", null),
}

/** A place in the app. Each module adds its detail screens here; `Root` is a sidebar entry's main screen. */
sealed interface Screen {
    /** [filters] pre-set list filters, e.g. the purchases of one supplier opened from the party screen. */
    data class Root(val section: Section, val filters: Map<String, String> = emptyMap()) : Screen
    data class PartyDetail(val partyId: String) : Screen
    data class PurchaseDetail(val purchaseId: String) : Screen
    data object SupplierPayables : Screen
    data class Movements(val productType: String? = null, val location: String? = null) : Screen
    data object ExternalWip : Screen
    data class OrderDetail(val orderId: String) : Screen
    data class PaymentDetail(val paymentId: String) : Screen
    data class AccountTransactions(val accountId: String, val accountName: String) : Screen
    data class Transactions(val accountId: String? = null) : Screen
    data class LoanDetail(val loanId: String) : Screen
    data class ChitDetail(val chitId: String) : Screen
    data class BatchDetail(val batchId: String) : Screen
    data class JobDetail(val jobId: String) : Screen
    data class Report(val reportKey: String) : Screen
    data object Notifications : Screen
}

/**
 * Forms and dialogs that open over the current screen (06 section 2: forms as a right-side panel,
 * short actions as dialogs). Pre-filled values travel with the overlay.
 */
sealed interface Overlay {
    val permission: String

    data class RecordPayment(val partyId: String? = null, val purpose: String? = null) : Overlay { override val permission = "PAYMENT_CREATE" }
    /** Ctrl+Shift+R: pick a purchase, then the receive dialog opens for it. */
    data class ReceiveSilk(val purchaseId: String? = null) : Overlay { override val permission = "PURCHASE_CREATE" }
    data object RecordExpense : Overlay { override val permission = "EXPENSE_CREATE" }
    data class RecordWork(val batchId: String? = null, val batchNumber: String? = null) : Overlay { override val permission = "WORK_RECORD_MANAGE" }
    data class OrderForm(val orderId: String? = null) : Overlay { override val permission = "ORDER_MANAGE" }
    data object NewPurchase : Overlay { override val permission = "PURCHASE_CREATE" }
    data class PartyForm(val partyId: String? = null) : Overlay { override val permission = "PARTY_MANAGE" }
    data object NewAccount : Overlay { override val permission = "FINANCE_MANAGE" }
    data object Transfer : Overlay { override val permission = "FINANCE_MANAGE" }
    data object NewLoan : Overlay { override val permission = "LOAN_MANAGE" }
    data object NewChit : Overlay { override val permission = "CHIT_MANAGE" }
    data class BatchForm(val batchId: String? = null) : Overlay { override val permission = "PRODUCTION_MANAGE" }
    data object NewJob : Overlay { override val permission = "OUTSOURCING_MANAGE" }
    data class AttachDocument(val referenceType: String? = null, val referenceId: String? = null) : Overlay {
        override val permission = "DOCUMENT_MANAGE"
    }
    data object ConfirmLogout : Overlay { override val permission = "" }
}

/**
 * One open screen and the view-models that belong to it. Clearing the store when the entry is closed ends
 * those view-models; while the entry is on the back stack they keep their state (06 section 5.2).
 */
class NavEntry(val screen: Screen) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

data class NavigationState(
    val user: MeResponse? = null,
    val sections: List<Section> = emptyList(),
    val stack: List<NavEntry> = emptyList(),
    val overlay: Overlay? = null,
    /** "Not allowed for your account", shown for 3 seconds after a blocked shortcut. */
    val hint: String? = null,
    val sidebarCollapsed: Boolean = false,
    /** Incremented by F5; screens reload when it changes. */
    val reloadTick: Int = 0,
    /** Incremented by Ctrl+F; list screens focus their search box when it changes. */
    val searchTick: Int = 0,
) {
    val current: NavEntry? get() = stack.lastOrNull()
    val section: Section? get() = (stack.firstOrNull()?.screen as? Screen.Root)?.section
}

/** Sidebar, back stack, overlays and global shortcuts (FR-AUTH-09, NFR-USE-01). */
class NavigationViewModel(private val settings: SettingsStore) : ViewModel() {

    private val _state = MutableStateFlow(NavigationState(sidebarCollapsed = settings.current.sidebarCollapsed))
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    /** Called after login: shows only the entries the user may see and opens the landing screen. */
    fun start(user: MeResponse) {
        val visible = Section.entries.filter { it.permission == null || user.can(it.permission) }
        // Dashboard for REPORT_VIEW, otherwise the stock summary (06 section 11 #7).
        val landing = if (user.can("REPORT_VIEW")) Section.DASHBOARD else Section.INVENTORY
        clearStack()
        _state.update { it.copy(user = user, sections = visible, stack = listOf(NavEntry(Screen.Root(landing))), overlay = null) }
    }

    fun can(permission: String): Boolean = permission.isEmpty() || _state.value.user?.can(permission) == true

    /** Sidebar click or Alt+n: a new root; the previous screens and their view-models are closed. */
    fun open(section: Section) {
        if (section !in _state.value.sections) return
        clearStack()
        _state.update { it.copy(stack = listOf(NavEntry(Screen.Root(section))), overlay = null) }
    }

    /** Alt+1 .. Alt+9: the n-th visible entry. */
    fun openNumber(number: Int) {
        _state.value.sections.getOrNull(number - 1)?.let(::open)
    }

    /** Opens a detail screen on top of the current one. */
    fun push(screen: Screen) = _state.update { it.copy(stack = it.stack + NavEntry(screen)) }

    /** Back button / Alt+Left; the root screen stays. */
    fun back() {
        val stack = _state.value.stack
        if (stack.size <= 1) return
        stack.last().viewModelStore.clear()
        _state.update { it.copy(stack = stack.dropLast(1)) }
    }

    /** Back action for a list opened from another screen (a filtered list), null for a sidebar root. */
    fun backIfNested(): (() -> Unit)? = if (_state.value.stack.size > 1) ::back else null

    /** Opens a form or dialog when the user has its permission; otherwise shows the 3-second hint. */
    fun show(overlay: Overlay): Boolean {
        if (!can(overlay.permission)) {
            _state.update { it.copy(hint = "common.notAllowed") }
            return false
        }
        _state.update { it.copy(overlay = overlay) }
        return true
    }

    fun closeOverlay() = _state.update { it.copy(overlay = null) }

    fun clearHint() = _state.update { it.copy(hint = null) }

    fun toggleSidebar() {
        val collapsed = !_state.value.sidebarCollapsed
        settings.update { it.copy(sidebarCollapsed = collapsed) }
        _state.update { it.copy(sidebarCollapsed = collapsed) }
    }

    fun reload() = _state.update { it.copy(reloadTick = it.reloadTick + 1) }

    fun focusSearch() = _state.update { it.copy(searchTick = it.searchTick + 1) }

    /** Ctrl+N: the create form of the module that is open. */
    fun newRecord() {
        val overlay = when (_state.value.section) {
            Section.PARTIES -> Overlay.PartyForm()
            Section.PURCHASES -> Overlay.NewPurchase
            Section.ORDERS -> Overlay.OrderForm()
            Section.PAYMENTS -> Overlay.RecordPayment()
            Section.PRODUCTION -> Overlay.BatchForm()
            Section.OUTSOURCING -> Overlay.NewJob
            Section.WORK -> Overlay.RecordWork()
            Section.MONEY -> Overlay.NewAccount
            Section.EXPENSES -> Overlay.RecordExpense
            Section.LOANS -> Overlay.NewLoan
            Section.CHITS -> Overlay.NewChit
            Section.DOCUMENTS -> Overlay.AttachDocument()
            else -> null
        }
        overlay?.let(::show)
    }

    private fun clearStack() = _state.value.stack.forEach { it.viewModelStore.clear() }

    override fun onCleared() = clearStack()
}
