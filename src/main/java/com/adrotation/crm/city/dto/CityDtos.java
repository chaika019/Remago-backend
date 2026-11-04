package com.adrotation.crm.city.dto;

import com.adrotation.crm.city.enums.CityCategory;
import com.adrotation.crm.city.enums.RepairStatus;
import com.adrotation.crm.city.enums.StandFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class CityDtos {
    private CityDtos() {}

    public record CityRequest(
            @NotBlank String name,
            @NotNull CityCategory category,
            boolean needsRepair,
            String standAddress,
            StandFormat standFormat,
            RepairStatus repairStatus,
            String repairPlan,
            Boolean rotationEnabled
    ) {}

    public record CityResponse(
            Long id,
            String name,
            CityCategory category,
            boolean needsRepair,
            String standAddress,
            StandFormat standFormat,
            RepairStatus repairStatus,
            String repairPlan,
            boolean rotationEnabled
    ) {}
}
