package com.adrotation.crm.shared.controller;

import com.adrotation.crm.city.enums.CityCategory;
import com.adrotation.crm.city.enums.RepairStatus;
import com.adrotation.crm.city.enums.StandFormat;
import com.adrotation.crm.company.enums.ClientStatus;
import com.adrotation.crm.company.enums.LegalForm;
import com.adrotation.crm.crm.enums.CalledToday;
import com.adrotation.crm.crm.enums.KanbanStage;
import com.adrotation.crm.crm.enums.ServiceType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/meta")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER','LAWYER','SHIFT_WORKER','BUH','DELO')")
@lombok.RequiredArgsConstructor
public class MetaController {
    private final com.adrotation.crm.crm.service.PlacementPolicy placementPolicy;

    @GetMapping
    public Map<String, Object> meta() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cityCategories", List.of(
                Map.of("code", CityCategory.A.name(), "label", "А"),
                Map.of("code", CityCategory.B.name(), "label", "Б"),
                Map.of("code", CityCategory.V.name(), "label", "В")
        ));
        result.put("mediaTypes", placementPolicy.metadata());
        result.put("standFormats", values(StandFormat.values()));
        result.put("repairStatuses", List.of(
                Map.of("code", RepairStatus.NONE.name(), "label", "Не требуется"),
                Map.of("code", RepairStatus.LIST_APPROVAL.name(), "label", "Согласование перечня"),
                Map.of("code", RepairStatus.PLANNED.name(), "label", "Запланирован"),
                Map.of("code", RepairStatus.IN_PROGRESS.name(), "label", "В работе"),
                Map.of("code", RepairStatus.COMPLETED.name(), "label", "Завершён")
        ));
        result.put("legalForms", Arrays.stream(LegalForm.values()).map(v -> Map.of("code", v.name(), "label", v.getDisplayName())).toList());
        result.put("clientStatuses", List.of(
                Map.of("code", ClientStatus.NEW.name(), "label", "Новый"),
                Map.of("code", ClientStatus.RETURN.name(), "label", "Возврат"),
                Map.of("code", ClientStatus.RENEWAL.name(), "label", "Продление")
        ));
        result.put("serviceTypes", List.of(
                Map.of("code", ServiceType.VACANCIES.name(), "label", "Вакансии"),
                Map.of("code", ServiceType.PROMOTION.name(), "label", "Продвижение")
        ));
        result.put("calledToday", List.of(
                Map.of("code", CalledToday.YES.name(), "label", "Да"),
                Map.of("code", CalledToday.NO.name(), "label", "Нет"),
                Map.of("code", CalledToday.NOT_CALLED.name(), "label", "Не звонила вообще")
        ));
        result.put("kanbanStages", List.of(
                Map.of("code", KanbanStage.PROSPECTIVE.name(), "label", "Перспективный"),
                Map.of("code", KanbanStage.IN_WORK.name(), "label", "В работе / новый лид"),
                Map.of("code", KanbanStage.GOING_TO_DEAL.name(), "label", "Идёт в сделку")
        ));
        return result;
    }

    private List<Map<String, String>> values(Enum<?>[] values) {
        return Arrays.stream(values).map(v -> Map.of("code", v.name(), "label", v.name())).toList();
    }
}
