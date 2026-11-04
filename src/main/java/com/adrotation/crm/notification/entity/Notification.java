package com.adrotation.crm.notification.entity;

import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.shared.entity.BaseEntity;
import com.adrotation.crm.notification.enums.NotificationChannel;
import com.adrotation.crm.notification.enums.NotificationTriggerType;
import com.adrotation.crm.user.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationTriggerType triggerType;

    @Column(nullable = false)
    private String entityType;

    private Long entityId;

    private String dedupeKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_user_id")
    private AppUser recipientUser;

    @Enumerated(EnumType.STRING)
    private Role recipientRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    private Instant sentAt;
    private Instant readAt;
}
