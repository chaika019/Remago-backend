package com.adrotation.crm.dashboard.dto;

public record DashboardSummaryDto(
        long plannedAndOccurred,
        long unplannedOccurred,
        long citiesChanged,
        long plannedNotOccurred,
        long noShiftWorker,
        long totalNotOccurred
) {}
