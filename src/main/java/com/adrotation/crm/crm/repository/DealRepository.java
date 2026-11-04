package com.adrotation.crm.crm.repository;

import com.adrotation.crm.crm.entity.Deal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DealRepository extends JpaRepository<Deal, Long> {

    @Query("select coalesce(max(d.dailySequence), 0) from Deal d where d.createdBy.id = :managerId and d.activityDate = :date")
    int maxDailySequence(@Param("managerId") Long managerId, @Param("date") LocalDate date);

    List<Deal> findByNextCallDate(LocalDate date);

    @Query("select d from Deal d where d.week.id = :weekId and (:managerId is null or d.createdBy.id = :managerId) order by d.activityDate, d.dailySequence")
    List<Deal> findForWeek(@Param("weekId") Long weekId, @Param("managerId") Long managerId);

    @Query("select distinct d from Deal d join fetch d.company where " +
            "((d.nextCallDate between :from and :to) or (d.nextActionDueDate between :from and :to)) " +
            "and (:managerId is null or d.createdBy.id = :managerId) order by d.activityDate")
    List<Deal> findCalendarDeals(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("managerId") Long managerId);

    boolean existsByCompany_IdAndCreatedBy_Id(Long companyId, Long managerId);

    List<Deal> findTop20ByCompany_CanonicalNameContainingIgnoreCaseOrderByUpdatedAtDesc(String query);
    List<Deal> findByCompany_IdOrderByActivityDateDesc(Long companyId);
}
