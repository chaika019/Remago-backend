package com.adrotation.crm.security.entity;

import com.adrotation.crm.shared.entity.BaseEntity;
import com.adrotation.crm.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "auth_attempts")
public class AuthAttempt extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(length = 255)
    private String email;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}
