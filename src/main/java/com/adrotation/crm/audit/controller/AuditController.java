package com.adrotation.crm.audit.controller;

import com.adrotation.crm.audit.dto.AuditLogDto;
import com.adrotation.crm.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {
    private final AuditService auditService;

    @GetMapping("/{entityType}/{entityId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','LAWYER')")
    public List<AuditLogDto> history(@PathVariable String entityType, @PathVariable Long entityId) {
        return auditService.history(entityType.toUpperCase(), entityId);
    }
}
