package com.adrotation.crm.documents.entity;

import com.adrotation.crm.company.entity.Company;
import com.adrotation.crm.shared.entity.BaseEntity;
import com.adrotation.crm.documents.enums.DocumentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "contracts", uniqueConstraints = @UniqueConstraint(name = "uk_contract_company_number", columnNames = {"company_id", "number"}))
public class Contract extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @Column(nullable = false) private String number;
    @Column(name = "contract_date", nullable = false) private LocalDate contractDate;
    private LocalDate validUntil;
    @Column(nullable = false) private boolean autoRenewal = true;
    @Column(nullable = false) private boolean active = true;
    @Enumerated(EnumType.STRING) @Column(name = "document_status", nullable = false) private DocumentStatus documentStatus = DocumentStatus.GENERATED;
    private String docxPath;
    private String pdfPath;
}
