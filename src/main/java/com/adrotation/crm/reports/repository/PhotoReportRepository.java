package com.adrotation.crm.reports.repository;

import com.adrotation.crm.reports.entity.PhotoReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhotoReportRepository extends JpaRepository<PhotoReport, Long> {
    List<PhotoReport> findByShift_IdOrderByCreatedAtDesc(Long shiftId);
}
