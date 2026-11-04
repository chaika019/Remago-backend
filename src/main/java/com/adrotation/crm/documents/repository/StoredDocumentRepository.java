package com.adrotation.crm.documents.repository;

import com.adrotation.crm.documents.entity.StoredDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredDocumentRepository extends JpaRepository<StoredDocument, Long> {
}
