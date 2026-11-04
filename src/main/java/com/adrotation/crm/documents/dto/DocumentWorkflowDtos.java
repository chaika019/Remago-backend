package com.adrotation.crm.documents.dto;

import com.adrotation.crm.documents.enums.DocumentStatus;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public final class DocumentWorkflowDtos {
    private DocumentWorkflowDtos() {}

    public record StatusRequest(@NotNull DocumentStatus status) {}
    public record StatusResponse(String entityType, Long entityId, DocumentStatus status) {}
    public record VersionResponse(Long id, int versionNumber, String note, String docxDownloadUrl, String pdfDownloadUrl,
                                  Long createdById, String createdByName, Instant createdAt) {}
    public record VersionsResponse(String entityType, Long entityId, List<VersionResponse> versions) {}
}
