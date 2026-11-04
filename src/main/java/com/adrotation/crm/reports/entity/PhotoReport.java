package com.adrotation.crm.reports.entity;

import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.shared.entity.BaseEntity;
import com.adrotation.crm.schedule.entity.Shift;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "photo_reports")
public class PhotoReport extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    private String driveFileId;
    private String driveWebViewLink;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private AppUser uploadedBy;

    private Instant sentToClientAt;
}
