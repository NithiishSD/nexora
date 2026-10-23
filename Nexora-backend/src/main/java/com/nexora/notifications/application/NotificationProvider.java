package com.nexora.notifications.application;

import com.nexora.notifications.domain.Notification;

// Delivers one notification (FR-NTF-04). The first release only logs (LoggingNotificationProvider); WhatsApp is a
// later implementation (FR-NTF-08). Called outside any database transaction; a failure is an exception.
public interface NotificationProvider {

    void send(Notification notification);
}
