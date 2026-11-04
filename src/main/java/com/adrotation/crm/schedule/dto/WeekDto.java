package com.adrotation.crm.schedule.dto;

import java.time.LocalDate;

public record WeekDto(
        Long id,
        int isoYear,
        int isoWeekNumber,
        LocalDate startDate,
        LocalDate endDate,
        String label,
        String driveFolderId,
        String driveFolderUrl
) {}
