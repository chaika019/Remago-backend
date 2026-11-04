package com.adrotation.crm.schedule.entity;

import com.adrotation.crm.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "weeks", uniqueConstraints = @UniqueConstraint(name = "uk_week_year_number", columnNames = {"iso_year", "iso_week_number"}))
public class WeekPeriod extends BaseEntity {
    @Column(name = "iso_year", nullable = false)
    private int isoYear;

    @Column(name = "iso_week_number", nullable = false)
    private int isoWeekNumber;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private String label;

    private String driveFolderId;
}
