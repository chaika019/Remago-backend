package com.adrotation.crm.documents.dto;

import com.adrotation.crm.documents.enums.DocumentLibrarySource;
import com.adrotation.crm.documents.enums.DocumentLibraryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DocumentLibraryDtos {

    private DocumentLibraryDtos() {
    }

    public record DocumentListItem(
            Long id,
            DocumentLibrarySource source,
            DocumentLibraryType type,
            String title,
            String number,
            LocalDate documentDate,
            Long companyId,
            String companyName,
            Long dealId,
            Long contractId,
            String status,
            BigDecimal amount,
            List<FileLink> files,
            Instant createdAt
    ) {
    }

    public record FileLink(
            String format,
            String fileName,
            String downloadUrl
    ) {
    }

    public record PageResponse(
            List<DocumentListItem> items,
            long totalElements,
            int page,
            int size,
            int totalPages
    ) {
    }

    public record SummaryResponse(
            long total,
            long specifications,
            long contracts,
            long invoices,
            long acts,
            long additionalAgreements,
            long other
    ) {
    }

    public record UploadResponse(
            Long id,
            DocumentLibraryType type,
            String title,
            String number,
            LocalDate documentDate,
            Long companyId,
            Long dealId,
            String originalFileName,
            String downloadUrl,
            Instant createdAt
    ) {
    }
}
