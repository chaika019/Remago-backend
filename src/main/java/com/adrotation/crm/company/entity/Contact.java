package com.adrotation.crm.company.entity;

import com.adrotation.crm.company.entity.Company;
import com.adrotation.crm.shared.entity.BaseEntity;
import com.adrotation.crm.company.enums.DeliveryChannel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "contacts")
public class Contact extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false) private String fio;
    private String position;
    private String phone;
    private String email;
    private String telegramChatId;
    private String viberUserId;
    @Enumerated(EnumType.STRING) private DeliveryChannel preferredDeliveryChannel;
}
