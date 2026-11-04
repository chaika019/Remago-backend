package com.adrotation.crm.audit.service;

import com.adrotation.crm.audit.dto.AuditLogDto;
import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.audit.entity.AuditLog;
import com.adrotation.crm.audit.repository.AuditLogRepository;
import com.adrotation.crm.auth.security.CurrentUserService;
import com.adrotation.crm.shared.exception.UnauthorizedException;
import com.adrotation.crm.shared.service.DtoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository auditLogRepository;
    private final CurrentUserService currentUserService;
    private final DtoMapper mapper;

    public void record(String entityType, Long entityId, String field, Object oldValue, Object newValue) {
        if (Objects.equals(oldValue, newValue)) return;
        AppUser author = null;
        try {
            author = currentUserService.requireCurrentUser();
        } catch (UnauthorizedException ignored) {
            // Scheduler/system changes may not have an authenticated user.
        }
        AuditLog log = new AuditLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setFieldName(field);
        log.setOldValue(stringify(oldValue));
        log.setNewValue(stringify(newValue));
        log.setAuthor(author);
        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> history(String entityType, Long entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId)
                .stream().map(mapper::audit).toList();
    }

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
