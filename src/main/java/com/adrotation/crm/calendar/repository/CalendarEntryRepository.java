package com.adrotation.crm.calendar.repository;

import com.adrotation.crm.calendar.entity.CalendarEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface CalendarEntryRepository extends JpaRepository<CalendarEntry, Long> {
    List<CalendarEntry> findByEventDateBetweenOrderByEventDateAscIdAsc(LocalDate from, LocalDate to);
}
