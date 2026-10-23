package com.nexora.notifications.application;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// FR-NTF-07: once a day at 08:30 Asia/Kolkata, check inventory balances against thresholds and create low-stock alerts.
@Component
public class LowStockAlertJob {

    private final NotificationService notifications;
    private final NotificationDispatcher dispatcher;
    private final Clock clock;

    public LowStockAlertJob(NotificationService notifications, NotificationDispatcher dispatcher, Clock clock) {
        this.notifications = notifications;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @Scheduled(cron = "${nexora.notifications.low-stock-cron:0 30 8 * * *}", zone = "Asia/Kolkata")
    public void runDaily() {
        run(LocalDate.now(clock));
        dispatcher.dispatchPending();
    }

    public int run(LocalDate today) {
        return notifications.createLowStockAlerts(today);
    }
}
