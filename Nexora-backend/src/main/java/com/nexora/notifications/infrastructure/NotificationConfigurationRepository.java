package com.nexora.notifications.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexora.notifications.domain.NotificationConfiguration;
import com.nexora.notifications.domain.NotificationType;

public interface NotificationConfigurationRepository extends JpaRepository<NotificationConfiguration, UUID> {

    Optional<NotificationConfiguration> findByNotificationType(NotificationType type);
}
