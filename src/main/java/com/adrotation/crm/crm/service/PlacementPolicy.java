package com.adrotation.crm.crm.service;

import com.adrotation.crm.crm.enums.MediaType;
import com.adrotation.crm.shared.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class PlacementPolicy {
    private final int staticMinimumDays;
    private final int staticPreparationDays;

    public PlacementPolicy(
            @Value("${app.placement.static-minimum-days:28}") int staticMinimumDays,
            @Value("${app.placement.static-preparation-days:7}") int staticPreparationDays) {
        if (staticMinimumDays < 1 || staticPreparationDays < 0) {
            throw new IllegalArgumentException("Некорректные сроки статического размещения");
        }
        this.staticMinimumDays = staticMinimumDays;
        this.staticPreparationDays = staticPreparationDays;
    }

    public void validate(MediaType type, LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            throw new BusinessException("Укажите корректный период размещения: окончание не раньше начала");
        }
        if (MediaType.orDefault(type) == MediaType.STATIC
                && ChronoUnit.DAYS.between(start, end) + 1 < staticMinimumDays) {
            throw new BusinessException("Статический баннер требует физической переклейки: минимальный период — "
                    + staticMinimumDays + " дней. LED-размещение выбирается отдельно.");
        }
    }

    public LocalDate preparationDeadline(MediaType type, LocalDate start) {
        return type == MediaType.LED || start == null ? null : start.minusDays(staticPreparationDays);
    }

    public List<Map<String, Object>> metadata() {
        return List.of(
                Map.of("code", "STATIC", "label", MediaType.STATIC.label(), "minimumDays", staticMinimumDays,
                        "preparationDays", staticPreparationDays, "physicalReplacement", true),
                Map.of("code", "LED", "label", MediaType.LED.label(), "minimumDays", 1,
                        "preparationDays", 0, "physicalReplacement", false));
    }
}
