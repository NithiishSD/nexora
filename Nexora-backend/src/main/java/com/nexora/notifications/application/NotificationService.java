package com.nexora.notifications.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.inventory.api.dto.LowStockItemResponse;
import com.nexora.inventory.application.InventoryThresholdService;
import com.nexora.notifications.api.dto.NotificationConfigurationRequest;
import com.nexora.notifications.api.dto.NotificationConfigurationResponse;
import com.nexora.notifications.api.dto.NotificationResponse;
import com.nexora.notifications.domain.Notification;
import com.nexora.notifications.domain.NotificationConfiguration;
import com.nexora.notifications.domain.NotificationStatus;
import com.nexora.notifications.domain.NotificationType;
import com.nexora.notifications.domain.Priority;
import com.nexora.notifications.domain.ReminderKind;
import com.nexora.notifications.infrastructure.NotificationConfigurationRepository;
import com.nexora.notifications.infrastructure.NotificationRepository;
import com.nexora.parties.application.PartyRef;
import com.nexora.parties.application.PartyService;
import com.nexora.procurement.api.dto.OpenPayable;
import com.nexora.procurement.application.SupplierPayableQuery;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.error.ResourceNotFoundException;

// Notification use cases (FR-NTF-01..03). Creating and resolving reminders only writes notification rows; sending
// them is a separate step after commit (NotificationDispatcher), so nothing here can affect money or stock.
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Set<NotificationStatus> OPEN = EnumSet.of(NotificationStatus.PENDING, NotificationStatus.SENT,
            NotificationStatus.FAILED);

    private final NotificationRepository notifications;
    private final NotificationConfigurationRepository configurations;
    private final SupplierPayableQuery payables;
    private final PartyService parties;
    private final InventoryThresholdService inventoryThresholdService;
    private final com.nexora.loans.application.LoanService loanService;
    private final com.nexora.chits.application.ChitService chitService;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, NotificationConfigurationRepository configurations,
                               SupplierPayableQuery payables, PartyService parties,
                               InventoryThresholdService inventoryThresholdService,
                               com.nexora.loans.application.LoanService loanService,
                               com.nexora.chits.application.ChitService chitService,
                               Clock clock) {
        this.notifications = notifications;
        this.configurations = configurations;
        this.payables = payables;
        this.parties = parties;
        this.inventoryThresholdService = inventoryThresholdService;
        this.loanService = loanService;
        this.chitService = chitService;
        this.clock = clock;
    }

    // E-NTF-02.
    @Transactional(readOnly = true)
    public List<NotificationConfigurationResponse> configurations() {
        return configurations.findAll().stream()
                .sorted((a, b) -> a.getNotificationType().compareTo(b.getNotificationType()))
                .map(NotificationService::toResponse).toList();
    }

    // E-NTF-03. Only seeded types have a row (the first release seeds SUPPLIER_DUE): any other is 404.
    @Transactional
    public NotificationConfigurationResponse updateConfiguration(NotificationType type,
                                                                 NotificationConfigurationRequest r) {
        NotificationConfiguration c = configurations.findByNotificationType(type)
                .orElseThrow(() -> new ResourceNotFoundException("Notification configuration", type));
        c.update(r.enabled(), r.daysBeforeDue(), r.overdueRepeatDays(), r.priority());
        log.info("event=notification_configuration_updated type={} enabled={} daysBeforeDue={} overdueRepeatDays={}",
                type, r.enabled(), r.daysBeforeDue(), r.overdueRepeatDays());
        return toResponse(configurations.saveAndFlush(c));
    }

    // E-NTF-01: newest first by default; from/to are business dates of scheduledAt (Asia/Kolkata), inclusive.
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(NotificationStatus status, NotificationType type, ReminderKind kind,
                                                   LocalDate from, LocalDate to, Pageable pageable) {
        ListParameters.requireSortable(pageable, Set.of("scheduledAt"));
        ListParameters.requireDateRange(from, to);
        Specification<Notification> filter = (root, query, cb) -> cb.conjunction();
        for (Specification<Notification> part : Arrays.asList(equal("status", status),
                equal("notificationType", type), equal("reminderKind", kind),
                from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("scheduledAt"),
                        from.atStartOfDay(clock.getZone()).toInstant()),
                to == null ? null : (root, query, cb) -> cb.lessThan(root.get("scheduledAt"),
                        to.plusDays(1).atStartOfDay(clock.getZone()).toInstant()))) {
            if (part != null) {
                filter = filter.and(part);
            }
        }
        Page<Notification> page = notifications.findAll(filter, pageable);
        Map<UUID, PartyRef> refs = parties.getRefs(page.getContent().stream().map(Notification::getRecipientPartyId)
                .filter(Objects::nonNull).distinct().toList());
        return PageResponse.from(page.map(n -> new NotificationResponse(n.getId(), n.getNotificationType(),
                n.getReminderKind(), n.getPriority(), n.getRecipientPartyId() == null ? null
                : refs.get(n.getRecipientPartyId()), n.getReferenceType(), n.getReferenceId(), n.getReferenceNumber(),
                n.getMessage(), n.getScheduledAt(), n.getSentAt(), n.getStatus(), n.getAttempts(), n.getCreatedAt(),
                n.getUpdatedAt())));
    }

    // E-NTF-04: Retry a failed notification (transitions FAILED -> PENDING).
    @Transactional
    public NotificationResponse retry(UUID id) {
        Notification n = notifications.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        n.retry();
        Notification saved = notifications.saveAndFlush(n);
        log.info("event=notification_retried id={}", id);
        PartyRef party = saved.getRecipientPartyId() == null ? null : parties.getRef(saved.getRecipientPartyId());
        return new NotificationResponse(saved.getId(), saved.getNotificationType(),
                saved.getReminderKind(), saved.getPriority(), party, saved.getReferenceType(),
                saved.getReferenceId(), saved.getReferenceNumber(), saved.getMessage(),
                saved.getScheduledAt(), saved.getSentAt(), saved.getStatus(), saved.getAttempts(),
                saved.getCreatedAt(), saved.getUpdatedAt());
    }

    // FR-NTF-02, run by the daily job for `today`: for every purchase still to be paid, DUE_SOON on
    // dueDate - daysBeforeDue, DUE_TODAY on the due date, OVERDUE every overdueRepeatDays after it. The dedupe key
    // (overdue repeats carry the date) makes a second run on the same day create nothing. Returns how many were made.
    @Transactional
    public int createSupplierDueReminders(LocalDate today) {
        NotificationConfiguration config = configurations.findByNotificationType(NotificationType.SUPPLIER_DUE)
                .orElseThrow(() -> new ResourceNotFoundException("Notification configuration",
                        NotificationType.SUPPLIER_DUE));
        resolvePaidPurchases();
        if (!config.isEnabled()) {
            return 0;
        }
        List<OpenPayable> due = payables.openPayables(null, null, null).stream()
                .filter(p -> p.purchaseId() != null && p.dueDate() != null)
                .toList();
        Map<UUID, PartyRef> suppliers = parties.getRefs(due.stream().map(OpenPayable::supplierId).distinct().toList());
        int created = 0;
        for (OpenPayable p : due) {
            long overdueDays = ChronoUnit.DAYS.between(p.dueDate(), today);
            ReminderKind kind = kindFor(overdueDays, config);
            if (kind == null) {
                continue;
            }
            String key = "SUPPLIER_DUE:" + p.purchaseId() + ":" + kind + (kind == ReminderKind.OVERDUE ? ":" + today : "");
            if (notifications.existsByDedupeKey(key)) {
                continue;
            }
            notifications.save(Notification.supplierDue(p.supplierId(), p.purchaseId(), p.reference(), kind,
                    config.getPriority(), message(kind, p, suppliers.get(p.supplierId()), overdueDays), key,
                    clock.instant()));
            created++;
        }
        log.info("event=supplier_due_reminders_created date={} count={}", today, created);
        return created;
    }

    // FR-NTF-03: open reminders of purchases that no longer have anything to pay (paid, or cancelled) are resolved, in
    // one UPDATE. It joins the caller's transaction: the daily job's, or a supplier payment's just before it commits
    // (SupplierPaymentListener), on the same connection.
    @Transactional
    public int resolvePaidPurchases() {
        Set<UUID> stillOwed = payables.openPayables(null, null, null).stream().map(OpenPayable::purchaseId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (stillOwed.isEmpty()) {
            stillOwed = Set.of(new UUID(0, 0));                // NOT IN needs at least one value
        }
        int resolved = notifications.resolveAllExcept(NotificationType.SUPPLIER_DUE, OPEN, stillOwed,
                NotificationStatus.RESOLVED, clock.instant());
        if (resolved > 0) {
            log.info("event=notifications_resolved count={}", resolved);
        }
        return resolved;
    }

    private static Specification<Notification> equal(String attribute, Object value) {
        return value == null ? null : (root, query, cb) -> cb.equal(root.get(attribute), value);
    }

    private static ReminderKind kindFor(long overdueDays, NotificationConfiguration config) {
        if (overdueDays == 0) {
            return ReminderKind.DUE_TODAY;
        }
        if (overdueDays > 0) {
            return overdueDays % config.getOverdueRepeatDays() == 0 ? ReminderKind.OVERDUE : null;
        }
        return config.getDaysBeforeDue() > 0 && -overdueDays == config.getDaysBeforeDue() ? ReminderKind.DUE_SOON : null;
    }

    private static String message(ReminderKind kind, OpenPayable p, PartyRef supplier, long overdueDays) {
        String what = "Pay Rs " + p.outstanding().toPlainString() + " to " + (supplier == null ? "the supplier"
                : supplier.name()) + " for " + p.reference();
        return switch (kind) {
            case DUE_SOON -> what + ": due in " + (-overdueDays) + " days (" + p.dueDate() + ").";
            case DUE_TODAY -> what + ": due today (" + p.dueDate() + ").";
            case OVERDUE -> what + ": overdue by " + overdueDays + (overdueDays == 1 ? " day" : " days")
                    + " (due " + p.dueDate() + ").";
        };
    }

    // FR-NTF-07: Low-stock alerts for inventory products/locations below their configured minimum threshold.
    @Transactional
    public int createLowStockAlerts(LocalDate today) {
        List<LowStockItemResponse> lowStockItems = inventoryThresholdService.getLowStockItems();
        int created = 0;
        Instant now = Instant.now(clock);

        for (LowStockItemResponse item : lowStockItems) {
            String dedupeKey = "LOW_STOCK:" + item.productType() + ":" + item.location() + ":" + today;
            if (notifications.existsByDedupeKey(dedupeKey)) {
                continue;
            }
            String message = "Low stock alert: " + item.productType() + " at " + item.location()
                    + " has " + item.currentQuantityKg() + " kg remaining (minimum threshold: "
                    + item.minThresholdKg() + " kg, deficit: " + item.deficitKg() + " kg).";

            notifications.save(Notification.lowStock(
                    null,
                    item.productType() + "@" + item.location(),
                    Priority.HIGH,
                    message,
                    dedupeKey,
                    now));
            created++;
        }
        return created;
    }

    // FR-NTF-08: Loan payment dues for active loans with configured due date.
    @Transactional
    public int createLoanDueReminders(LocalDate today) {
        NotificationConfiguration loanConfig = configurations.findByNotificationType(NotificationType.LOAN_DUE).orElse(null);
        if (loanConfig != null && !loanConfig.isEnabled()) {
            return 0;
        }
        int daysBeforeDue = loanConfig != null ? loanConfig.getDaysBeforeDue() : 3;
        int overdueRepeatDays = loanConfig != null ? loanConfig.getOverdueRepeatDays() : 1;
        Priority priority = loanConfig != null ? loanConfig.getPriority() : Priority.HIGH;

        List<com.nexora.loans.api.dto.LoanResponse> activeLoans = loanService.all().stream()
                .filter(l -> l.status() == com.nexora.loans.domain.LoanStatus.ACTIVE)
                .filter(l -> l.dueDate() != null)
                .filter(l -> l.outstandingPrincipal().compareTo(java.math.BigDecimal.ZERO) > 0)
                .toList();

        int created = 0;
        for (com.nexora.loans.api.dto.LoanResponse loan : activeLoans) {
            long overdueDays = ChronoUnit.DAYS.between(loan.dueDate(), today);
            ReminderKind kind = kindFor(overdueDays, daysBeforeDue, overdueRepeatDays);
            if (kind == null) {
                continue;
            }
            String key = "LOAN_DUE:" + loan.id() + ":" + kind + (kind == ReminderKind.OVERDUE ? ":" + today : "");
            if (notifications.existsByDedupeKey(key)) {
                continue;
            }
            String lenderName = loan.lender() != null ? loan.lender().name() : "Lender";
            String what = "Pay loan " + loan.loanNumber() + " (outstanding principal: Rs "
                    + loan.outstandingPrincipal().toPlainString() + ") to " + lenderName;
            String message = switch (kind) {
                case DUE_SOON -> what + ": due in " + (-overdueDays) + " days (" + loan.dueDate() + ").";
                case DUE_TODAY -> what + ": due today (" + loan.dueDate() + ").";
                case OVERDUE -> what + ": overdue by " + overdueDays + (overdueDays == 1 ? " day" : " days")
                        + " (due " + loan.dueDate() + ").";
            };

            notifications.save(Notification.loanDue(
                    loan.lender() != null ? loan.lender().id() : null,
                    loan.id(),
                    loan.loanNumber(),
                    kind,
                    priority,
                    message,
                    key,
                    clock.instant()));
            created++;
        }
        if (created > 0) {
            log.info("event=loan_due_reminders_created date={} count={}", today, created);
        }
        return created;
    }

    // Resolves open LOAN_DUE reminders for loans that are closed or fully settled.
    @Transactional
    public int resolveClosedLoans() {
        Set<UUID> stillOwed = loanService.all().stream()
                .filter(l -> l.status() == com.nexora.loans.domain.LoanStatus.ACTIVE && l.outstandingPrincipal().compareTo(java.math.BigDecimal.ZERO) > 0)
                .map(com.nexora.loans.api.dto.LoanResponse::id)
                .collect(Collectors.toSet());
        if (stillOwed.isEmpty()) {
            stillOwed = Set.of(new UUID(0, 0));
        }
        int resolved = notifications.resolveAllExcept(NotificationType.LOAN_DUE, OPEN, stillOwed,
                NotificationStatus.RESOLVED, clock.instant());
        if (resolved > 0) {
            log.info("event=loan_notifications_resolved count={}", resolved);
        }
        return resolved;
    }

    // FR-NTF-09: Monthly chit fund contribution due reminders.
    @Transactional
    public int createChitDueReminders(LocalDate today) {
        NotificationConfiguration chitConfig = configurations.findByNotificationType(NotificationType.CHIT_DUE).orElse(null);
        if (chitConfig != null && !chitConfig.isEnabled()) {
            return 0;
        }
        int daysBeforeDue = chitConfig != null ? chitConfig.getDaysBeforeDue() : 3;
        int overdueRepeatDays = chitConfig != null ? chitConfig.getOverdueRepeatDays() : 1;
        Priority priority = chitConfig != null ? chitConfig.getPriority() : Priority.HIGH;

        List<com.nexora.chits.api.dto.ChitResponse> activeChits = chitService.all().stream()
                .filter(c -> c.status() == com.nexora.chits.domain.ChitStatus.ACTIVE)
                .filter(c -> c.remainingExpectedContributions() > 0)
                .toList();

        int created = 0;
        for (com.nexora.chits.api.dto.ChitResponse chit : activeChits) {
            int installmentNumber = chit.contributionsCount() + 1;
            LocalDate installmentDueDate = chit.startDate().plusMonths(installmentNumber - 1);
            long overdueDays = ChronoUnit.DAYS.between(installmentDueDate, today);
            ReminderKind kind = kindFor(overdueDays, daysBeforeDue, overdueRepeatDays);
            if (kind == null) {
                continue;
            }
            String key = "CHIT_DUE:" + chit.id() + ":M" + installmentNumber + ":" + kind
                    + (kind == ReminderKind.OVERDUE ? ":" + today : "");
            if (notifications.existsByDedupeKey(key)) {
                continue;
            }
            String what = "Pay monthly chit contribution Rs " + chit.monthlyAmount().toPlainString()
                    + " for installment #" + installmentNumber + " of " + chit.chitName() + " (" + chit.chitNumber() + ")";
            String message = switch (kind) {
                case DUE_SOON -> what + ": due in " + (-overdueDays) + " days (" + installmentDueDate + ").";
                case DUE_TODAY -> what + ": due today (" + installmentDueDate + ").";
                case OVERDUE -> what + ": overdue by " + overdueDays + (overdueDays == 1 ? " day" : " days")
                        + " (due " + installmentDueDate + ").";
            };

            notifications.save(Notification.chitDue(
                    chit.id(),
                    chit.chitNumber(),
                    kind,
                    priority,
                    message,
                    key,
                    clock.instant()));
            created++;
        }
        if (created > 0) {
            log.info("event=chit_due_reminders_created date={} count={}", today, created);
        }
        return created;
    }

    // Resolves open CHIT_DUE reminders for chits that are closed or installments already contributed.
    @Transactional
    public int resolveCompletedChits() {
        List<com.nexora.chits.api.dto.ChitResponse> allChits = chitService.all();
        Set<UUID> stillActive = allChits.stream()
                .filter(c -> c.status() == com.nexora.chits.domain.ChitStatus.ACTIVE && c.remainingExpectedContributions() > 0)
                .map(com.nexora.chits.api.dto.ChitResponse::id)
                .collect(Collectors.toSet());
        if (stillActive.isEmpty()) {
            stillActive = Set.of(new UUID(0, 0));
        }
        int resolved = notifications.resolveAllExcept(NotificationType.CHIT_DUE, OPEN, stillActive,
                NotificationStatus.RESOLVED, clock.instant());

        List<Notification> openChitNotifications = notifications.findByNotificationTypeAndStatusIn(NotificationType.CHIT_DUE, OPEN);
        Map<UUID, com.nexora.chits.api.dto.ChitResponse> chitMap = allChits.stream().collect(Collectors.toMap(com.nexora.chits.api.dto.ChitResponse::id, c -> c));

        for (Notification n : openChitNotifications) {
            com.nexora.chits.api.dto.ChitResponse chit = chitMap.get(n.getReferenceId());
            if (chit != null) {
                int currentInstallment = chit.contributionsCount() + 1;
                for (int i = 1; i < currentInstallment; i++) {
                    if (n.getDedupeKey().contains(":M" + i + ":")) {
                        n.resolve();
                        resolved++;
                        break;
                    }
                }
            }
        }
        if (resolved > 0) {
            log.info("event=chit_notifications_resolved count={}", resolved);
        }
        return resolved;
    }

    private static ReminderKind kindFor(long overdueDays, int daysBeforeDue, int overdueRepeatDays) {
        if (overdueDays == 0) {
            return ReminderKind.DUE_TODAY;
        }
        if (overdueDays > 0) {
            return overdueDays % overdueRepeatDays == 0 ? ReminderKind.OVERDUE : null;
        }
        return daysBeforeDue > 0 && -overdueDays == daysBeforeDue ? ReminderKind.DUE_SOON : null;
    }

    private static NotificationConfigurationResponse toResponse(NotificationConfiguration c) {
        return new NotificationConfigurationResponse(c.getNotificationType(), c.isEnabled(), c.getDaysBeforeDue(),
                c.getOverdueRepeatDays(), c.getPriority(), c.getUpdatedAt());
    }
}
