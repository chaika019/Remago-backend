package com.adrotation.crm.crm.dto;

import com.adrotation.crm.city.dto.CityDtos;
import com.adrotation.crm.city.enums.StandFormat;
import com.adrotation.crm.company.dto.CompanyDtos;
import com.adrotation.crm.crm.enums.CalledToday;
import com.adrotation.crm.crm.enums.DocumentIntent;
import com.adrotation.crm.crm.enums.KanbanStage;
import com.adrotation.crm.crm.enums.ServiceType;
import com.adrotation.crm.documents.enums.DocumentPackageType;
import com.adrotation.crm.schedule.dto.WeekDto;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import com.adrotation.crm.crm.enums.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public final class DealDtos {
    private DealDtos() {}

    public record PlacementRequest(
            @NotNull Long cityId,
            @NotNull StandFormat standFormat,
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd,
            @NotNull @Positive Integer quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal discountPercent,
            String promotionName,
            Boolean vatExempt,
            @DecimalMin("0.00") BigDecimal vatRate,
            MediaType mediaType
    ) {}

    public record PlacementResponse(
            Long id,
            Long cityId,
            String cityName,
            StandFormat standFormat,
            LocalDate periodStart,
            LocalDate periodEnd,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal discountPercent,
            String promotionName,
            boolean vatExempt,
            BigDecimal vatRate,
            MediaType mediaType
    ) {}

    public record CreateDealRequest(
            @NotNull Long companyId,
            @NotEmpty Set<Long> targetCityIds,
            @NotNull ServiceType serviceType,
            Long dailyGoalId,
            boolean goalAchieved,
            CalledToday calledToday,
            DocumentIntent documentIntent,
            List<@NotNull @Valid PlacementRequest> placements,
            LocalDate nextCallDate,
            String nextAction,
            LocalDate nextActionDueDate,
            String comment,
            boolean prospective,
            LocalDate activityDate,
            Long managerId
    ) {}

    public record UpdateDealRequest(
            Long companyId,
            Set<Long> targetCityIds,
            ServiceType serviceType,
            Long dailyGoalId,
            Boolean goalAchieved,
            CalledToday calledToday,
            DocumentIntent documentIntent,
            List<@NotNull @Valid PlacementRequest> placements,
            LocalDate nextCallDate,
            Boolean clearNextCallDate,
            String nextAction,
            LocalDate nextActionDueDate,
            Boolean clearNextAction,
            Boolean completeNextAction,
            String comment,
            Boolean prospective,
            LocalDate activityDate
    ) {}

    public record DealResponse(
            Long id,
            int dailySequence,
            LocalDate activityDate,
            WeekDto week,
            CompanyDtos.CompanyResponse company,
            Set<CityDtos.CityResponse> targetCities,
            ServiceType serviceType,
            GoalOptionDto dailyGoal,
            boolean goalAchieved,
            CalledToday calledToday,
            DocumentIntent documentIntent,
            List<PlacementResponse> placements,
            boolean documentGenerationEligible,
            DocumentPackageType recommendedDocumentPackage,
            LocalDate nextCallDate,
            String nextAction,
            LocalDate nextActionDueDate,
            Instant nextActionCompletedAt,
            boolean nextActionOverdue,
            String comment,
            boolean prospective,
            KanbanStage kanbanStage,
            Long managerId,
            String managerName
    ) {}
}
