package com.adrotation.crm.reports.dto;

import java.time.Instant;

public record PhotoReportDto(
        Long id,
        Long shiftId,
        String driveFileId,
        String driveWebViewLink,
        Long uploadedById,
        String uploadedByName,
        Instant uploadedAt,
        Instant sentToClientAt
) {}
