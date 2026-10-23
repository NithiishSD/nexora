package com.nexora.notifications.application;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// FR-NTF-08: once a day at 08:15 Asia/Kolkata, check active loans and create due reminders.
@Component
public class LoanDueReminderJob {

    private final NotificationService notifications;
    private final NotificationDispatcher dispatcher;
    private final Clock clock;

    public LoanDueReminderJob(NotificationService notifications, NotificationDispatcher dispatcher, Clock clock) {
        this.notifications = notifications;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @Scheduled(cron = "${nexora.notifications.loan-due-cron:0 15 8 * * *}", zone = "Asia/Kolkata")
    public void runDaily() {
        run(LocalDate.now(clock));
        dispatcher.dispatchPending();
    }

    public int run(LocalDate today) {
        notifications.resolveClosedLoans();
        return notifications.createLoanDueReminders(today);
    }
}
