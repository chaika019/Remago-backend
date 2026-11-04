package com.adrotation.crm.documents.entity;

import com.adrotation.crm.company.entity.Company;
import com.adrotation.crm.crm.entity.Deal;
import com.adrotation.crm.shared.entity.BaseEntity;
import com.adrotation.crm.documents.enums.DocumentLibraryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
        name = "stored_documents",
        indexes = {
                @Index(name = "idx_stored_documents_type_date", columnList = "document_type,document_date"),
                @Index(name = "idx_stored_documents_company", columnList = "company_id"),
                @Index(name = "idx_stored_documents_deal", columnList = "deal_id")
        }
)
public class StoredDocument extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private DocumentLibraryType documentType;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 150)
    private String number;

    @Column(name = "document_date")
    private LocalDate documentDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id")
    private Deal deal;

    @Column(name = "original_file_name", nullable = false, length = 500)
    private String originalFileName;

    @Column(name = "stored_path", nullable = false, length = 1000)
    private String storedPath;

    @Column(name = "mime_type", length = 255)
    private String mimeType;

    @Column(columnDefinition = "text")
    private String notes;
}
