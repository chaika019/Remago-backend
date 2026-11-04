package com.adrotation.crm.schedule.repository;

import com.adrotation.crm.schedule.entity.Shift;
import com.adrotation.crm.schedule.enums.ShiftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    List<Shift> findByWeek_IdIn(Collection<Long> weekIds);

    boolean existsByCity_IdAndWeek_Id(Long cityId, Long weekId);

    @Query("select s from Shift s join fetch s.week w left join fetch s.city left join fetch s.responsiblePerson where s.status = :status and w.startDate between :fromDate and :toDate")
    List<Shift> findByStatusAndWeekStartDateBetween(
            @Param("status") ShiftStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("select distinct s from Shift s join fetch s.week w join fetch s.city left join fetch s.company left join fetch s.responsiblePerson " +
            "where w.startDate <= :toDate and w.endDate >= :fromDate order by w.startDate, s.city.name")
    List<Shift> findForCalendar(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
