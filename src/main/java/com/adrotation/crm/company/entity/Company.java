package com.adrotation.crm.company.entity;

import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.shared.entity.BaseEntity;
import com.adrotation.crm.company.enums.ClientStatus;
import com.adrotation.crm.company.enums.LegalForm;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "companies")
public class Company extends BaseEntity {
    @Enumerated(EnumType.STRING) @Column(nullable = false) private LegalForm legalForm;
    @Column(nullable = false) private String shortName;
    @Column(nullable = false, unique = true) private String canonicalName;

    // В текущем проекте поле inn используем как УНП. Имя колонки не меняем, чтобы не ломать API/БД.
    @Column(unique = true) private String inn;

    @Enumerated(EnumType.STRING) @Column(nullable = false) private ClientStatus status = ClientStatus.NEW;

    @Column(columnDefinition = "text") private String legalAddress;
    @Column(columnDefinition = "text") private String postalAddress;
    private String settlementAccount;
    private String bankName;
    private String bankBic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private AppUser createdBy;
}
