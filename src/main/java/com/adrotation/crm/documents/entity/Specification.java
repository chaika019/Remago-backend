package com.adrotation.crm.documents.entity;

import com.adrotation.crm.company.entity.Company;
import com.adrotation.crm.crm.entity.Deal;
import com.adrotation.crm.shared.entity.BaseEntity;
import com.adrotation.crm.documents.entity.Contract;
import com.adrotation.crm.documents.enums.DocumentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "specifications")
public class Specification extends BaseEntity {
    @Column(nullable = false, unique = true) private Long number;
    @Column(nullable = false) private LocalDate specificationDate;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "deal_id") private Deal deal;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id", nullable = false) private Company company;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "contract_id") private Contract contract;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal totalBeforeDiscount;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal totalAfterDiscount;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal vatAmount;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DocumentStatus status = DocumentStatus.GENERATED;
    private String docxPath;
    private String pdfPath;
    @OneToMany(mappedBy = "specification", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber asc") private List<SpecificationItem> items = new ArrayList<>();
}
