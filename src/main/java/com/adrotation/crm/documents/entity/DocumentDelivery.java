package com.adrotation.crm.documents.entity;

import com.adrotation.crm.company.entity.Contact;
import com.adrotation.crm.shared.entity.BaseEntity;
import com.adrotation.crm.company.enums.DeliveryChannel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "document_deliveries")
public class DocumentDelivery extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "specification_id", nullable = false) private Specification specification;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "contact_id") private Contact contact;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DeliveryChannel channel;
    private String recipient;
    @Column(nullable = false) private String status;
    private Instant sentAt;
    @Column(columnDefinition = "text") private String errorMessage;
}
