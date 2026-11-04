package com.adrotation.crm.crm.entity;

import com.adrotation.crm.city.entity.City;
import com.adrotation.crm.city.enums.StandFormat;
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
@Table(name = "deal_placements")
public class DealPlacement extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deal_id", nullable = false) private Deal deal;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false) private City city;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private StandFormat standFormat;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MediaType mediaType = MediaType.STATIC;
    @Column(nullable = false) private LocalDate periodStart;
    @Column(nullable = false) private LocalDate periodEnd;
    @Column(nullable = false) private Integer quantity;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal unitPrice;
    @Column(nullable = false, precision = 6, scale = 2) private BigDecimal discountPercent = BigDecimal.ZERO;
    private String promotionName;
    @Column(nullable = false) private boolean vatExempt = true;
    @Column(nullable = false, precision = 6, scale = 2) private BigDecimal vatRate = BigDecimal.ZERO;
}
