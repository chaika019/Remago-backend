package com.adrotation.crm.documents.entity;

import com.adrotation.crm.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "organization_profiles")
public class OrganizationProfile extends BaseEntity {
    @Column(nullable = false) private String legalName;
    @Column(nullable = false) private String unp;
    @Column(nullable = false) private String settlementAccount;
    @Column(nullable = false) private String bankName;
    @Column(nullable = false) private String bankBic;
    private String bankCity;
    @Column(nullable = false, columnDefinition = "text") private String legalAddress;
    @Column(nullable = false, columnDefinition = "text") private String postalAddress;
    @Column(columnDefinition = "text") private String registrationInfo;
    private String signatoryName;
}
