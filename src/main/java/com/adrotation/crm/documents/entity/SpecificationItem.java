package com.adrotation.crm.documents.entity;

import com.adrotation.crm.shared.entity.BaseEntity;
import jakarta.persistence.*;
import com.adrotation.crm.crm.enums.MediaType;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "specification_items")
public class SpecificationItem extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "specification_id", nullable = false) private Specification specification;
    @Column(nullable = false) private Integer lineNumber;
    @Column(nullable = false) private String cityName;
    @Column(nullable = false) private String standFormat;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MediaType mediaType = MediaType.STATIC;
    @Column(nullable = false) private LocalDate periodStart;
    @Column(nullable = false) private LocalDate periodEnd;
    @Column(nullable = false) private Integer quantity;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal unitPrice;
    @Column(nullable = false, precision = 6, scale = 2) private BigDecimal discountPercent;
    private String promotionName;
    @Column(nullable = false) private boolean vatExempt;
    @Column(nullable = false, precision = 6, scale = 2) private BigDecimal vatRate;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal vatAmount;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal totalBeforeDiscount;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal totalAfterDiscount;
}
