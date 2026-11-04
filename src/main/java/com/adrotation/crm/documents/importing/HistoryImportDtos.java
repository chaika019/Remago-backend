package com.adrotation.crm.documents.importing;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class HistoryImportDtos {
    private HistoryImportDtos() {}

    public record DriveSourceRequest(@NotBlank String urlOrFileId) {}

    public record PreviewRow(
            int rowNumber,
            String documentType,
            String companyName,
            String legalForm,
            String unp,
            String number,
            LocalDate documentDate,
            String contractNumber,
            LocalDate contractDate,
            BigDecimal amount,
            String linkedFileUrl,
            String action,
            List<String> warnings
    ) {}

    public record PreviewResponse(String sourceName, int totalRows, int creatableRows, int duplicateRows,
                                  int warningRows, List<PreviewRow> rows) {}

    public record CommitResponse(Long batchId, int importedRows, int skippedRows, int errorRows, List<String> errors) {}
}
