package com.adrotation.crm.documents.entity;

import com.adrotation.crm.shared.entity.BaseEntity;
import com.adrotation.crm.user.entity.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "import_batches")
public class ImportBatch extends BaseEntity {
    @Column(name = "source_type", nullable = false, length = 40) private String sourceType;
    @Column(name = "source_name", length = 500) private String sourceName;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by_id") private AppUser createdBy;
    @Column(name = "imported_rows", nullable = false) private int importedRows;
    @Column(name = "skipped_rows", nullable = false) private int skippedRows;
    @Column(name = "error_rows", nullable = false) private int errorRows;
    @Column(name = "details_json", columnDefinition = "text") private String detailsJson;
}
