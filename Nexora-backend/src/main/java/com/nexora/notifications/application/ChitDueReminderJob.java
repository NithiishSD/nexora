package com.nexora.notifications.application;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// FR-NTF-09: once a day at 08:20 Asia/Kolkata, check active chits and create monthly installment due reminders.
@Component
public class ChitDueReminderJob {

    private final NotificationService notifications;
    private final NotificationDispatcher dispatcher;
    private final Clock clock;

    public ChitDueReminderJob(NotificationService notifications, NotificationDispatcher dispatcher, Clock clock) {
        this.notifications = notifications;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @Scheduled(cron = "${nexora.notifications.chit-due-cron:0 20 8 * * *}", zone = "Asia/Kolkata")
    public void runDaily() {
        run(LocalDate.now(clock));
        dispatcher.dispatchPending();
    }

    public int run(LocalDate today) {
        notifications.resolveCompletedChits();
        return notifications.createChitDueReminders(today);
    }
}
