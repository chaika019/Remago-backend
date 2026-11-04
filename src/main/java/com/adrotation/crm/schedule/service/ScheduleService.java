package com.adrotation.crm.schedule.service;

import com.adrotation.crm.schedule.dto.ScheduleDtos;
import com.adrotation.crm.city.entity.City;
import com.adrotation.crm.schedule.entity.Shift;
import com.adrotation.crm.schedule.entity.WeekPeriod;
import com.adrotation.crm.city.enums.CityCategory;
import com.adrotation.crm.schedule.enums.ShiftStatus;
import com.adrotation.crm.city.repository.CityRepository;
import com.adrotation.crm.schedule.repository.ShiftRepository;
import com.adrotation.crm.shared.service.DtoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleService {
    private final CityRepository cityRepository;
    private final ShiftRepository shiftRepository;
    private final WeekService weekService;
    private final DtoMapper mapper;

    @Transactional
    public ScheduleDtos.ScheduleResponse get(int year, int weekFrom, int weekTo, CityCategory category) {
        List<WeekPeriod> weeks = weekService.range(year, weekFrom, weekTo);
        if (weeks.size() != weekTo - weekFrom + 1) {
            for (int number = weekFrom; number <= weekTo; number++) weekService.requireByIsoWeek(year, number);
            weeks = weekService.range(year, weekFrom, weekTo);
        }
        List<City> cities = category == null ? cityRepository.findAllByOrderByCategoryAscNameAsc() : cityRepository.findByCategoryOrderByNameAsc(category);
        List<Long> weekIds = weeks.stream().map(WeekPeriod::getId).toList();
        List<Shift> shifts = weekIds.isEmpty() ? List.of() : shiftRepository.findByWeek_IdIn(weekIds);

        Map<String, Shift> latestByCell = shifts.stream().collect(Collectors.toMap(
                s -> s.getCity().getId() + ":" + s.getWeek().getId(),
                Function.identity(),
                (a, b) -> a.getUpdatedAt().isAfter(b.getUpdatedAt()) ? a : b
        ));

        List<ScheduleDtos.CityRow> rows = new ArrayList<>();
        for (City city : cities) {
            if (!city.isRotationEnabled()) continue;
            List<ScheduleDtos.ShiftCell> cells = new ArrayList<>();
            for (WeekPeriod week : weeks) {
                Shift shift = latestByCell.get(city.getId() + ":" + week.getId());
                if (city.isNeedsRepair()) {
                    ShiftStatus repair = ShiftStatus.REPAIR_AND_RECONSTRUCTION;
                    cells.add(new ScheduleDtos.ShiftCell(week.getId(), week.getIsoWeekNumber(), shift == null ? null : shift.getId(), repair, repair.getDisplayName(), repair.getColorHex(), null, null));
                } else if (shift == null) {
                    cells.add(new ScheduleDtos.ShiftCell(week.getId(), week.getIsoWeekNumber(), null, ShiftStatus.PLANNED, ShiftStatus.PLANNED.getDisplayName(), ShiftStatus.PLANNED.getColorHex(), null, null));
                } else {
                    cells.add(new ScheduleDtos.ShiftCell(week.getId(), week.getIsoWeekNumber(), shift.getId(), shift.getStatus(), shift.getStatus().getDisplayName(), shift.getStatus().getColorHex(),
                            shift.getCompany() == null ? null : shift.getCompany().getId(), shift.getCompany() == null ? null : shift.getCompany().getCanonicalName()));
                }
            }
            rows.add(new ScheduleDtos.CityRow(city.getId(), city.getName(), city.getCategory(), city.isNeedsRepair(), cells));
        }
        return new ScheduleDtos.ScheduleResponse(year, weeks.stream().map(mapper::week).toList(), rows);
    }
}
