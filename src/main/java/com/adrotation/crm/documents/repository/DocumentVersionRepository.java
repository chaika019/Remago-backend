package com.adrotation.crm.documents.repository;

import com.adrotation.crm.documents.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    List<DocumentVersion> findByEntityTypeAndEntityIdOrderByVersionNumberDesc(String entityType, Long entityId);
    long countByEntityTypeAndEntityId(String entityType, Long entityId);
}
