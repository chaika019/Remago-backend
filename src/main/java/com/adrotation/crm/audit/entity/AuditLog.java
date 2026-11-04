package com.adrotation.crm.audit.entity;

import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {
    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private Long entityId;

    @Column(nullable = false)
    private String fieldName;

    @Column(columnDefinition = "text")
    private String oldValue;

    @Column(columnDefinition = "text")
    private String newValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private AppUser author;
}
