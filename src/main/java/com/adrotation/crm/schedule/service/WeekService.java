package com.adrotation.crm.schedule.service;

import com.adrotation.crm.config.GoogleDriveProperties;
import com.adrotation.crm.schedule.dto.WeekDto;
import com.adrotation.crm.schedule.entity.WeekPeriod;
import com.adrotation.crm.shared.exception.NotFoundException;
import com.adrotation.crm.schedule.repository.WeekRepository;
import com.adrotation.crm.shared.service.DtoMapper;
import com.adrotation.crm.shared.drive.DriveStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WeekService {
    private static final Locale RU = Locale.forLanguageTag("ru-RU");
    private static final Pattern WEEK_NUMBER = Pattern.compile("(?:^|\\D)(\\d{1,2})(?:\\D|$)");

    private final WeekRepository weekRepository;
    private final DriveStorageService driveStorageService;
    private final GoogleDriveProperties googleDriveProperties;
    private final DtoMapper mapper;

    @Transactional
    public List<WeekDto> syncYear(int year) {
        int maxWeek = LocalDate.of(year, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        for (int weekNumber = 1; weekNumber <= maxWeek; weekNumber++) {
            WeekPeriod week = weekRepository.findByIsoYearAndIsoWeekNumber(year, weekNumber).orElseGet(WeekPeriod::new);
            LocalDate start = weekStart(year, weekNumber);
            week.setIsoYear(year);
            week.setIsoWeekNumber(weekNumber);
            week.setStartDate(start);
            week.setEndDate(start.plusDays(6));
            week.setLabel(buildLabel(start, start.plusDays(6)));
            weekRepository.save(week);
        }
        syncDriveFolderMapping(year);
        return weekRepository.findByIsoYearOrderByIsoWeekNumberAsc(year).stream().map(mapper::week).toList();
    }

    @Transactional
    public void syncDriveFolderMapping(int year) {
        if (!driveStorageService.isEnabled() || googleDriveProperties.rootFolderId() == null || googleDriveProperties.rootFolderId().isBlank()) {
            return;
        }
        Map<Integer, DriveStorageService.DriveFolder> byWeek = driveStorageService.listChildFolders(googleDriveProperties.rootFolderId())
                .stream()
                .filter(folder -> extractWeek(folder.name()) != null)
                .map(folder -> Map.entry(extractWeek(folder.name()), folder))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));

        List<WeekPeriod> weeks = weekRepository.findByIsoYearOrderByIsoWeekNumberAsc(year);
        for (WeekPeriod week : weeks) {
            DriveStorageService.DriveFolder folder = byWeek.get(week.getIsoWeekNumber());
            if (folder != null && !folder.id().equals(week.getDriveFolderId())) {
                week.setDriveFolderId(folder.id());
                weekRepository.save(week);
            }
        }
    }

    @Transactional
    public WeekPeriod requireByDate(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("date is required");
        return weekRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(date, date)
                .orElseGet(() -> {
                    int isoYear = date.get(IsoFields.WEEK_BASED_YEAR);
                    int weekNumber = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                    WeekPeriod week = new WeekPeriod();
                    LocalDate start = weekStart(isoYear, weekNumber);
                    week.setIsoYear(isoYear);
                    week.setIsoWeekNumber(weekNumber);
                    week.setStartDate(start);
                    week.setEndDate(start.plusDays(6));
                    week.setLabel(buildLabel(start, start.plusDays(6)));
                    return weekRepository.save(week);
                });
    }


    @Transactional
    public WeekPeriod requireByIsoWeek(int year, int weekNumber) {
        validateWeekNumber(year, weekNumber);
        return weekRepository.findByIsoYearAndIsoWeekNumber(year, weekNumber)
                .orElseGet(() -> {
                    LocalDate start = weekStart(year, weekNumber);
                    WeekPeriod week = new WeekPeriod();
                    week.setIsoYear(year);
                    week.setIsoWeekNumber(weekNumber);
                    week.setStartDate(start);
                    week.setEndDate(start.plusDays(6));
                    week.setLabel(buildLabel(start, start.plusDays(6)));
                    return weekRepository.save(week);
                });
    }

    @Transactional(readOnly = true)
    public java.util.Optional<WeekPeriod> findByIsoWeek(int year, int weekNumber) {
        validateWeekNumber(year, weekNumber);
        return weekRepository.findByIsoYearAndIsoWeekNumber(year, weekNumber);
    }

    public WeekPeriod require(Long id) {
        return weekRepository.findById(id).orElseThrow(() -> new NotFoundException("Неделя не найдена: " + id));
    }

    @Transactional(readOnly = true)
    public List<WeekDto> list(int year) {
        return weekRepository.findByIsoYearOrderByIsoWeekNumberAsc(year).stream().map(mapper::week).toList();
    }

    @Transactional(readOnly = true)
    public List<WeekPeriod> range(int year, int weekFrom, int weekTo) {
        validateWeekNumber(year, weekFrom);
        validateWeekNumber(year, weekTo);
        if (weekFrom > weekTo) {
            throw new IllegalArgumentException("weekFrom must be <= weekTo");
        }
        return weekRepository.findByIsoYearAndIsoWeekNumberBetweenOrderByIsoWeekNumberAsc(year, weekFrom, weekTo);
    }

    private void validateWeekNumber(int year, int weekNumber) {
        int maxWeek = LocalDate.of(year, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (weekNumber < 1 || weekNumber > maxWeek) {
            throw new IllegalArgumentException(
                    "Неделя должна быть в диапазоне 1–" + maxWeek + " для " + year + " года"
            );
        }
    }

    private LocalDate weekStart(int isoYear, int weekNumber) {
        LocalDate anchor = LocalDate.of(isoYear, 1, 4);
        LocalDate monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.plusWeeks(weekNumber - 1L);
    }

    private String buildLabel(LocalDate start, LocalDate end) {
        if (start.getMonth() == end.getMonth()) {
            String month = end.getMonth().getDisplayName(TextStyle.FULL, RU);
            return start.getDayOfMonth() + "–" + end.getDayOfMonth() + " " + month;
        }
        String startMonth = start.getMonth().getDisplayName(TextStyle.FULL, RU);
        String endMonth = end.getMonth().getDisplayName(TextStyle.FULL, RU);
        return start.getDayOfMonth() + " " + startMonth + " – " + end.getDayOfMonth() + " " + endMonth;
    }

    private Integer extractWeek(String folderName) {
        if (folderName == null) return null;
        Matcher matcher = WEEK_NUMBER.matcher(folderName);
        if (!matcher.find()) return null;
        int value = Integer.parseInt(matcher.group(1));
        return value >= 1 && value <= 53 ? value : null;
    }
}
