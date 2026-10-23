package com.nexora.notifications.application;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// FR-NTF-02: once a day at 08:00 Asia/Kolkata, create the supplier due-date reminders for today and send them.
@Component
public class SupplierDueReminderJob {

    private final NotificationService notifications;
    private final NotificationDispatcher dispatcher;
    private final Clock clock;

    public SupplierDueReminderJob(NotificationService notifications, NotificationDispatcher dispatcher, Clock clock) {
        this.notifications = notifications;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    // Create (one committed transaction), then send (after the commit, outside it).
    @Scheduled(cron = "${nexora.notifications.supplier-due-cron:0 0 8 * * *}", zone = "Asia/Kolkata")
    public void runDaily() {
        run(LocalDate.now(clock));
        dispatcher.dispatchPending();
    }

    // The day is a parameter so the schedule can be replayed for any date (tests, a missed run).
    public int run(LocalDate today) {
        return notifications.createSupplierDueReminders(today);
    }
}
