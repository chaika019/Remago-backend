package com.adrotation.crm.documents.entity;

import com.adrotation.crm.shared.entity.BaseEntity;
import com.adrotation.crm.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "document_versions", uniqueConstraints = @UniqueConstraint(name = "uk_document_version", columnNames = {"entity_type", "entity_id", "version_number"}))
public class DocumentVersion extends BaseEntity {
    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;
    @Column(name = "entity_id", nullable = false)
    private Long entityId;
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;
    @Column(name = "docx_path", length = 1000)
    private String docxPath;
    @Column(name = "pdf_path", length = 1000)
    private String pdfPath;
    @Column(length = 500)
    private String note;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private AppUser createdBy;
}
