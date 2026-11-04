package com.adrotation.crm.city.entity;

import com.adrotation.crm.shared.entity.BaseEntity;
import com.adrotation.crm.city.enums.CityCategory;
import com.adrotation.crm.city.enums.RepairStatus;
import com.adrotation.crm.city.enums.StandFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "cities")
public class City extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private boolean rotationEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CityCategory category;

    @Column(nullable = false)
    private boolean needsRepair;

    private String standAddress;

    @Enumerated(EnumType.STRING)
    private StandFormat standFormat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairStatus repairStatus = RepairStatus.NONE;

    @Column(columnDefinition = "text")
    private String repairPlan;
}
