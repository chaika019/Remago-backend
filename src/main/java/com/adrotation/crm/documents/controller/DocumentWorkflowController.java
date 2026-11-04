package com.adrotation.crm.documents.controller;

import com.adrotation.crm.documents.dto.DocumentWorkflowDtos;
import com.adrotation.crm.documents.entity.DocumentVersion;
import com.adrotation.crm.documents.service.DocumentVersionService;
import com.adrotation.crm.documents.service.DocumentWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
public class DocumentWorkflowController {
    private final DocumentWorkflowService workflowService;
    private final DocumentVersionService versionService;

    @PatchMapping("/api/contracts/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BUH','DELO')")
    public DocumentWorkflowDtos.StatusResponse contractStatus(@PathVariable Long id, @Valid @RequestBody DocumentWorkflowDtos.StatusRequest r) {
        return workflowService.contract(id, r.status());
    }

    @PatchMapping("/api/specifications/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BUH','DELO')")
    public DocumentWorkflowDtos.StatusResponse specificationStatus(@PathVariable Long id, @Valid @RequestBody DocumentWorkflowDtos.StatusRequest r) {
        return workflowService.specification(id, r.status());
    }

    @GetMapping("/api/document-versions/{entityType}/{entityId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BUH','DELO')")
    public DocumentWorkflowDtos.VersionsResponse versions(@PathVariable String entityType, @PathVariable Long entityId) {
        return versionService.list(entityType, entityId);
    }

    @GetMapping("/api/document-versions/{versionId}/download/{format}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BUH','DELO')")
    public ResponseEntity<Resource> download(@PathVariable Long versionId, @PathVariable String format) {
        DocumentVersion v = versionService.require(versionId);
        Resource resource = versionService.load(versionId, format);
        String ext = "pdf".equalsIgnoreCase(format) ? "pdf" : "docx";
        MediaType media = ext.equals("pdf") ? MediaType.APPLICATION_PDF : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        String filename = v.getEntityType().toLowerCase() + "-" + v.getEntityId() + "-v" + v.getVersionNumber() + "." + ext;
        return ResponseEntity.ok().contentType(media)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }
}
