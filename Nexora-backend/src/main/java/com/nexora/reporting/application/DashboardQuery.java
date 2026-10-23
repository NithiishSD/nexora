package com.nexora.reporting.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.api.dto.CashPositionResponse;
import com.nexora.finance.application.AllocationQueryService;
import com.nexora.finance.application.FinancialAccountService;
import com.nexora.finance.domain.PaymentPurpose;
import com.nexora.inventory.api.dto.StockSummaryResponse;
import com.nexora.inventory.application.InventoryQueryService;
import com.nexora.loans.application.LoanService;
import com.nexora.procurement.api.dto.SupplierPayablesOverviewResponse;
import com.nexora.procurement.application.SupplierPayableQuery;
import com.nexora.reporting.api.dto.DashboardResponse;
import com.nexora.reporting.infrastructure.DashboardRepository;
import com.nexora.reporting.infrastructure.DashboardRepository.Counts;
import com.nexora.shared.domain.Money;

// FR-RPT-01: the owner's dashboard in one call. Each money figure comes from the module that owns its formula, so it
// equals that module's own endpoint (AC1). REPEATABLE_READ gives every query the same snapshot: the figures agree.
@Service
public class DashboardQuery {

    private static final int DUE_WITHIN_DAYS = 7;
    private static final int RECENT_TRANSACTIONS = 10;

    private final FinancialAccountService accounts;
    private final AllocationQueryService allocations;
    private final SupplierPayableQuery supplierPayables;
    private final InventoryQueryService inventory;
    private final LoanService loans;
    private final DashboardRepository dashboard;
    private final Clock clock;

    public DashboardQuery(FinancialAccountService accounts, AllocationQueryService allocations,
                          SupplierPayableQuery supplierPayables, InventoryQueryService inventory, LoanService loans,
                          DashboardRepository dashboard, Clock clock) {
        this.accounts = accounts;
        this.allocations = allocations;
        this.supplierPayables = supplierPayables;
        this.inventory = inventory;
        this.loans = loans;
        this.dashboard = dashboard;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardResponse summary() {
        CashPositionResponse money = accounts.cashPosition(clock.instant());
        SupplierPayablesOverviewResponse suppliers = supplierPayables.overview(false, "outstanding,desc");
        StockSummaryResponse stock = inventory.summary();
        Counts counts = dashboard.counts(LocalDate.now(clock));
        Map<String, Long> orders = new LinkedHashMap<>();
        orders.put("PLACED", counts.placed());
        orders.put("CONFIRMED", counts.confirmed());
        orders.put("IN_PROGRESS", counts.inProgress());
        orders.put("READY", counts.ready());
        Map<String, Long> batches = new LinkedHashMap<>();
        batches.put("PLANNED", counts.batchesPlanned());
        batches.put("IN_PROGRESS", counts.batchesInProgress());
        return new DashboardResponse(money.asOf(), money.totalCash(), money.banks(), money.totalAvailable(),
                Money.round(allocations.totalOutstanding(PaymentPurpose.CUSTOMER)),
                Money.round(allocations.totalCustomerAdvance()),
                suppliers.totalPayable(), suppliers.totalOverdue(),
                Money.round(allocations.totalOutstanding(PaymentPurpose.MANUFACTURER)),
                Money.round(allocations.totalOutstanding(PaymentPurpose.WORKER)),
                loans.activeOutstandingPrincipal(), Money.round(counts.chitMonthly()),
                new DashboardResponse.Stock(stock.rawSilkAvailableKg(), stock.internalWipKg(), stock.externalWipKg(),
                        stock.finishedSilkWarpKg(), stock.finishedKoraWarpKg(), stock.vudaWarpKg()),
                orders, counts.late(), batches, counts.openJobs(),
                supplierPayables.upcomingDues(DUE_WITHIN_DAYS),
                accounts.transactions(null, null, null, null, null, null, null,
                        PageRequest.of(0, RECENT_TRANSACTIONS, Sort.by(Sort.Direction.DESC, "createdAt"))).content());
    }
}
