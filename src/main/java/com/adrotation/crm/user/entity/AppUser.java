package com.adrotation.crm.user.entity;

import com.adrotation.crm.user.enums.Permission;
import com.adrotation.crm.user.enums.Role;
import com.adrotation.crm.shared.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "app_users")
public class AppUser extends BaseEntity {
    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "email_verification_code_hash", length = 255)
    private String emailVerificationCodeHash;

    @Column(name = "email_verification_expires_at")
    private Instant emailVerificationExpiresAt;

    @Column(name = "email_verification_attempts", nullable = false)
    private int emailVerificationAttempts = 0;

    @Column(name = "email_verification_last_sent_at")
    private Instant emailVerificationLastSentAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Set<Role> roles = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false)
    private Set<Permission> permissions = new HashSet<>();
}
