package com.adrotation.crm.notification.dto;

import com.adrotation.crm.notification.enums.NotificationChannel;
import com.adrotation.crm.notification.enums.NotificationTriggerType;
import com.adrotation.crm.user.enums.Role;

import java.time.Instant;

public record NotificationDto(
        Long id,
        NotificationTriggerType triggerType,
        String entityType,
        Long entityId,
        String dedupeKey,
        Long recipientUserId,
        Role recipientRole,
        NotificationChannel channel,
        String title,
        String payload,
        Instant sentAt,
        Instant readAt,
        Instant createdAt
) {}
