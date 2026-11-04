package com.adrotation.crm.documents.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "document_sequences")
public class DocumentSequence {
    @Id private String code;
    @Column(nullable = false) private Long nextValue;
    @Column(nullable = false) private Instant updatedAt;
    @PrePersist @PreUpdate void touch() { updatedAt = Instant.now(); }
}
