package com.adrotation.crm.company.workspace;

import com.adrotation.crm.audit.dto.AuditLogDto;
import com.adrotation.crm.company.dto.CompanyDtos;
import com.adrotation.crm.crm.dto.DealDtos;
import com.adrotation.crm.documents.enums.DocumentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CompanyWorkspaceDtos {
    private CompanyWorkspaceDtos() {}

    public record DocumentSummary(String type, Long id, String number, LocalDate date, DocumentStatus status,
                                  BigDecimal amount, String downloadDocxUrl, String downloadPdfUrl) {}

    public record Response(
            CompanyDtos.CompanyResponse company,
            List<CompanyDtos.ContactResponse> contacts,
            List<DealDtos.DealResponse> deals,
            boolean documentsVisible,
            List<DocumentSummary> documents,
            List<AuditLogDto> timeline
    ) {}
}
