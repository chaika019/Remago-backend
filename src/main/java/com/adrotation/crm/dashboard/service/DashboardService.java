package com.adrotation.crm.dashboard.service;

import com.adrotation.crm.dashboard.dto.DashboardSummaryDto;
import com.adrotation.crm.schedule.entity.Shift;
import com.adrotation.crm.schedule.entity.WeekPeriod;
import com.adrotation.crm.schedule.enums.ShiftStatus;
import com.adrotation.crm.schedule.repository.ShiftRepository;
import com.adrotation.crm.schedule.service.WeekService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final WeekService weekService;
    private final ShiftRepository shiftRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryDto summary(int year, int weekFrom, int weekTo) {
        List<WeekPeriod> weeks = weekService.range(year, weekFrom, weekTo);
        List<Shift> shifts = weeks.isEmpty() ? List.of() : shiftRepository.findByWeek_IdIn(weeks.stream().map(WeekPeriod::getId).toList());

        Set<ShiftStatus> plannedOccurredStatuses = Set.of(
                ShiftStatus.SHIFT_AND_NOTIFICATION, ShiftStatus.PLUS_MINUS, ShiftStatus.AUTO_SHIFT, ShiftStatus.AUTO_AND_SHIFT
        );
        long plannedAndOccurred = shifts.stream().filter(s -> plannedOccurredStatuses.contains(s.getStatus())).count();
        long unplannedOccurred = shifts.stream().filter(s -> s.getStatus() == ShiftStatus.WHITE_SHIFT).count();
        Set<ShiftStatus> occurredStatuses = new java.util.HashSet<>(plannedOccurredStatuses);
        occurredStatuses.add(ShiftStatus.WHITE_SHIFT);
        long citiesChanged = shifts.stream().filter(s -> occurredStatuses.contains(s.getStatus())).map(s -> s.getCity().getId()).distinct().count();
        long plannedNotOccurred = shifts.stream().filter(s -> s.getStatus() == ShiftStatus.NOT_OCCURRED).count();
        long noShiftWorker = shifts.stream().filter(s -> s.getStatus() == ShiftStatus.MINUS).count();
        long totalNotOccurred = plannedNotOccurred + noShiftWorker;
        return new DashboardSummaryDto(plannedAndOccurred, unplannedOccurred, citiesChanged, plannedNotOccurred, noShiftWorker, totalNotOccurred);
    }
}
