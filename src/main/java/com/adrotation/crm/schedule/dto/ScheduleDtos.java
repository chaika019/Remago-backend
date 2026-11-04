package com.adrotation.crm.schedule.dto;

import com.adrotation.crm.city.enums.CityCategory;
import com.adrotation.crm.schedule.enums.ShiftStatus;

import java.util.List;

public final class ScheduleDtos {
    private ScheduleDtos() {}

    public record ScheduleResponse(
            int year,
            List<WeekDto> weeks,
            List<CityRow> rows
    ) {}

    public record CityRow(
            Long cityId,
            String cityName,
            CityCategory category,
            boolean needsRepair,
            List<ShiftCell> cells
    ) {}

    public record ShiftCell(
            Long weekId,
            int isoWeekNumber,
            Long shiftId,
            ShiftStatus status,
            String label,
            String colorHex,
            Long companyId,
            String companyName
    ) {}
}
