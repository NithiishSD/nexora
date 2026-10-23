package com.nexora.notifications.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.nexora.notifications.application.NotificationProvider;
import com.nexora.notifications.domain.Notification;

// The first release's only delivery channel (FR-NTF-08): the reminder is written to the application log, where the
// owner's monitoring picks it up. The message names the supplier and purchase, never an account number.
@Component
public class LoggingNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationProvider.class);

    @Override
    public void send(Notification n) {
        log.info("event=notification_sent id={} type={} kind={} reference={} message=\"{}\"", n.getId(),
                n.getNotificationType(), n.getReminderKind(), n.getReferenceNumber(), n.getMessage());
    }
}
