package com.nexora.notifications.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.notifications.domain.Notification;
import com.nexora.notifications.domain.NotificationStatus;
import com.nexora.notifications.domain.NotificationType;

public interface NotificationRepository extends JpaRepository<Notification, UUID>,
        JpaSpecificationExecutor<Notification> {

    boolean existsByDedupeKey(String dedupeKey);

    List<Notification> findByNotificationTypeAndStatusIn(NotificationType type, Collection<NotificationStatus> statuses);

    List<Notification> findByStatusOrderByScheduledAt(NotificationStatus status);

    // Bulk resolution: open reminders of a type whose reference is not among the given ids (the version is bumped so a
    // dispatcher holding a stale copy cannot overwrite it).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.status = :resolved, n.updatedAt = :now, n.version = n.version + 1"
            + " where n.notificationType = :type and n.status in :open and n.referenceId not in :keep")
    int resolveAllExcept(@Param("type") NotificationType type, @Param("open") Collection<NotificationStatus> open,
                         @Param("keep") Collection<UUID> keep, @Param("resolved") NotificationStatus resolved,
                         @Param("now") Instant now);
}
