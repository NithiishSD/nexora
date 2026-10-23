package com.nexora.procurement.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.parties.application.PartyRef;
import com.nexora.parties.application.PartyService;
import com.nexora.parties.domain.PartyRole;
import com.nexora.procurement.api.dto.OpenPayable;
import com.nexora.procurement.api.dto.SupplierDueResponse;
import com.nexora.procurement.api.dto.SupplierPayableResponse;
import com.nexora.procurement.api.dto.SupplierPayableResponse.OpeningPayable;
import com.nexora.procurement.api.dto.SupplierPayableResponse.PurchasePayable;
import com.nexora.procurement.api.dto.SupplierPayablesOverviewResponse;
import com.nexora.procurement.api.dto.SupplierPayablesOverviewResponse.SupplierLine;
import com.nexora.procurement.domain.PurchaseStatus;
import com.nexora.procurement.infrastructure.PayableRepository;
import com.nexora.procurement.infrastructure.PayableRepository.OpeningRow;
import com.nexora.procurement.infrastructure.PayableRepository.PurchaseRow;
import com.nexora.shared.domain.Money;
import com.nexora.shared.error.FieldValidationException;

// What the business owes its suppliers (FR-PAY-19), computed on read: never stored.
@Service
public class SupplierPayableQuery {

    private static final Set<String> SORTABLE = Set.of("outstanding", "oldestDueDate");

    private final PayableRepository payables;
    private final PartyService parties;
    private final Clock clock;

    public SupplierPayableQuery(PayableRepository payables, PartyService parties, Clock clock) {
        this.payables = payables;
        this.parties = parties;
        this.clock = clock;
    }

    // E-PAY-07. 404 unknown, 422 not a supplier; an inactive supplier's payables are still shown.
    @Transactional(readOnly = true)
    public SupplierPayableResponse forSupplier(UUID supplierId) {
        PartyRef supplier = parties.requireRole(supplierId, PartyRole.SUPPLIER);
        LocalDate today = LocalDate.now(clock);
        List<PurchasePayable> purchases = new ArrayList<>();
        for (PurchaseRow row : payables.purchases(supplierId)) {
            PurchasePayment pay = payment(row, today);
            if (pay.outstanding().signum() != 0 && row.status() != PurchaseStatus.CANCELLED) {
                purchases.add(new PurchasePayable(row.purchaseId(), row.purchaseNumber(), row.purchaseDate(),
                        row.dueDate(), pay.payable(), pay.allocated(), pay.outstanding(), pay.paymentStatus(),
                        pay.overdue(), pay.overdueDays()));
            }
        }
        List<OpeningPayable> openings = payables.openingPayables(supplierId).stream()
                .filter(o -> o.originalAmount().compareTo(o.allocated()) != 0)
                .map(o -> new OpeningPayable(o.id(), o.obligationDate(), o.dueDate(), o.referenceNote(),
                        o.originalAmount(), o.allocated(), o.originalAmount().subtract(o.allocated()),
                        (int) ChronoUnit.DAYS.between(o.obligationDate(), today)))
                .toList();
        Totals t = new Totals();
        purchases.forEach(p -> t.add(p.outstanding(), p.dueDate(), today));
        openings.forEach(o -> t.add(o.outstanding(), o.dueDate(), today));
        return new SupplierPayableResponse(supplier, t.total, t.overdue, t.dueSoon, purchases, openings, today);
    }

    // E-PAY-08: one line per supplier with something outstanding.
    @Transactional(readOnly = true)
    public SupplierPayablesOverviewResponse overview(boolean overdueOnly, String sort) {
        String[] parts = sort.split(",");
        if (!SORTABLE.contains(parts[0])) {
            throw new FieldValidationException("sort", "Sorting by '" + parts[0] + "' is not supported; use"
                    + " outstanding or oldestDueDate.");
        }
        LocalDate today = LocalDate.now(clock);
        Map<UUID, Totals> bySupplier = new LinkedHashMap<>();
        Totals all = new Totals();
        for (PurchaseRow row : payables.purchases(null)) {
            PurchasePayment pay = payment(row, today);
            if (pay.outstanding().signum() > 0 && row.status() != PurchaseStatus.CANCELLED) {
                bySupplier.computeIfAbsent(row.supplierId(), k -> new Totals()).add(pay.outstanding(), row.dueDate(), today);
                all.add(pay.outstanding(), row.dueDate(), today);
            }
        }
        for (OpeningRow o : payables.openingPayables(null)) {
            BigDecimal outstanding = o.originalAmount().subtract(o.allocated());
            if (outstanding.signum() > 0) {
                bySupplier.computeIfAbsent(o.supplierId(), k -> new Totals()).add(outstanding, o.dueDate(), today);
                all.add(outstanding, o.dueDate(), today);
            }
        }
        Map<UUID, PartyRef> refs = parties.getRefs(bySupplier.keySet());
        Comparator<SupplierLine> order = parts[0].equals("outstanding")
                ? Comparator.comparing(SupplierLine::outstanding)
                : Comparator.comparing(SupplierLine::oldestDueDate, Comparator.nullsLast(Comparator.naturalOrder()));
        if (parts.length > 1 && parts[1].equalsIgnoreCase("desc")) {
            order = order.reversed();
        }
        List<SupplierLine> lines = bySupplier.entrySet().stream()
                .filter(e -> !overdueOnly || e.getValue().overdue.signum() > 0)
                .map(e -> new SupplierLine(refs.get(e.getKey()), e.getValue().total, e.getValue().overdue,
                        e.getValue().oldestDue))
                .sorted(order)
                .toList();
        return new SupplierPayablesOverviewResponse(all.total, all.overdue, all.dueSoon, lines);
    }

    // Everything still owed to suppliers (outstanding > 0), the same items the E-PAY-08 overview totals, optionally
    // only one supplier's and only those dated within [from, to] (purchase or opening date). For the ageing report.
    @Transactional(readOnly = true)
    public List<OpenPayable> openPayables(UUID supplierId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        List<OpenPayable> open = new ArrayList<>();
        for (PurchaseRow row : payables.purchases(supplierId)) {
            BigDecimal outstanding = payment(row, today).outstanding();
            if (row.status() != PurchaseStatus.CANCELLED && outstanding.signum() > 0) {
                open.add(new OpenPayable(row.supplierId(), row.purchaseId(), row.purchaseNumber(), row.purchaseDate(),
                        row.dueDate(), outstanding));
            }
        }
        for (OpeningRow o : payables.openingPayables(supplierId)) {
            BigDecimal outstanding = o.originalAmount().subtract(o.allocated());
            if (outstanding.signum() > 0) {
                open.add(new OpenPayable(o.supplierId(), null, "Opening SUP " + o.obligationDate(), o.obligationDate(),
                        o.dueDate(), outstanding));
            }
        }
        return open.stream()
                .filter(p -> (from == null || !p.date().isBefore(from)) && (to == null || !p.date().isAfter(to)))
                .toList();
    }

    // FR-NTF-06: purchases with something to pay that are due within the next `days` days or already overdue, earliest
    // due date first; live from the payables, whether or not a reminder was ever sent.
    @Transactional(readOnly = true)
    public List<SupplierDueResponse> upcomingDues(int days) {
        LocalDate today = LocalDate.now(clock);
        List<PurchaseRow> due = new ArrayList<>();
        for (PurchaseRow row : payables.purchases(null)) {
            if (row.status() != PurchaseStatus.CANCELLED && payment(row, today).outstanding().signum() > 0
                    && !row.dueDate().isAfter(today.plusDays(days))) {
                due.add(row);
            }
        }
        Map<UUID, PartyRef> refs = parties.getRefs(due.stream().map(PurchaseRow::supplierId).distinct().toList());
        return due.stream()
                .sorted(Comparator.comparing(PurchaseRow::dueDate).thenComparing(PurchaseRow::purchaseNumber))
                .map(row -> {
                    PurchasePayment pay = payment(row, today);
                    return new SupplierDueResponse(row.purchaseId(), row.purchaseNumber(), refs.get(row.supplierId()),
                            row.dueDate(), pay.outstanding(), pay.overdue(), pay.overdueDays());
                })
                .toList();
    }

    private static PurchasePayment payment(PurchaseRow row, LocalDate today) {
        return PurchasePayment.of(row.payable(), row.allocated(), row.dueDate(), row.status(), today);
    }

    // Running sums: everything outstanding, the overdue part, and what falls due within the next 7 days.
    private static final class Totals {
        private BigDecimal total = Money.zero();
        private BigDecimal overdue = Money.zero();
        private BigDecimal dueSoon = Money.zero();
        private LocalDate oldestDue;

        void add(BigDecimal outstanding, LocalDate dueDate, LocalDate today) {
            total = total.add(outstanding);
            if (dueDate != null && outstanding.signum() > 0) {
                if (dueDate.isBefore(today)) {
                    overdue = overdue.add(outstanding);
                } else if (!dueDate.isAfter(today.plusDays(7))) {
                    dueSoon = dueSoon.add(outstanding);
                }
                oldestDue = oldestDue == null || dueDate.isBefore(oldestDue) ? dueDate : oldestDue;
            }
        }
    }
}
