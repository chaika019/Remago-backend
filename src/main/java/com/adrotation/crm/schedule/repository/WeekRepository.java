package com.adrotation.crm.schedule.repository;

import com.adrotation.crm.schedule.entity.WeekPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeekRepository extends JpaRepository<WeekPeriod, Long> {
    Optional<WeekPeriod> findByIsoYearAndIsoWeekNumber(int isoYear, int isoWeekNumber);
    Optional<WeekPeriod> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate date1, LocalDate date2);
    List<WeekPeriod> findByIsoYearAndIsoWeekNumberBetweenOrderByIsoWeekNumberAsc(int isoYear, int weekFrom, int weekTo);
    List<WeekPeriod> findByIsoYearOrderByIsoWeekNumberAsc(int isoYear);
}
