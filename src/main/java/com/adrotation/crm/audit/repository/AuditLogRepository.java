package com.adrotation.crm.audit.repository;

import com.adrotation.crm.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);
    List<AuditLog> findByEntityTypeAndEntityIdInOrderByCreatedAtDesc(String entityType, java.util.Collection<Long> entityIds);
}
