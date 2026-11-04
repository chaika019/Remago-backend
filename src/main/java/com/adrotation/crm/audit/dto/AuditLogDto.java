package com.adrotation.crm.audit.dto;

import java.time.Instant;

public record AuditLogDto(
        Long id,
        String entityType,
        Long entityId,
        String fieldName,
        String oldValue,
        String newValue,
        Long authorId,
        String authorName,
        Instant timestamp
) {}
