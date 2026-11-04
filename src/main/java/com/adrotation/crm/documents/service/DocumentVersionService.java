package com.adrotation.crm.documents.service;

import com.adrotation.crm.auth.security.CurrentUserService;
import com.adrotation.crm.documents.dto.DocumentWorkflowDtos;
import com.adrotation.crm.documents.entity.DocumentVersion;
import com.adrotation.crm.documents.repository.DocumentVersionRepository;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.shared.exception.NotFoundException;
import com.adrotation.crm.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DocumentVersionService {
    private final DocumentVersionRepository repository;
    private final CurrentUserService currentUserService;
    private final LocalDocumentStorageService storage;

    @Transactional
    public DocumentVersion capture(String entityType, Long entityId, String docxPath, String pdfPath, String note) {
        if ((docxPath == null || docxPath.isBlank()) && (pdfPath == null || pdfPath.isBlank())) return null;
        String type = normalizeType(entityType);
        DocumentVersion version = new DocumentVersion();
        version.setEntityType(type);
        version.setEntityId(entityId);
        version.setVersionNumber((int) repository.countByEntityTypeAndEntityId(type, entityId) + 1);
        version.setDocxPath(docxPath);
        version.setPdfPath(pdfPath);
        version.setNote(note == null || note.isBlank() ? "Сформированная версия" : note.trim());
        try { version.setCreatedBy(currentUserService.requireCurrentUser()); } catch (RuntimeException ignored) {}
        return repository.save(version);
    }

    @Transactional(readOnly = true)
    public DocumentWorkflowDtos.VersionsResponse list(String entityType, Long entityId) {
        String type = normalizeType(entityType);
        List<DocumentWorkflowDtos.VersionResponse> versions = repository.findByEntityTypeAndEntityIdOrderByVersionNumberDesc(type, entityId)
                .stream().map(this::dto).toList();
        return new DocumentWorkflowDtos.VersionsResponse(type, entityId, versions);
    }

    @Transactional(readOnly = true)
    public DocumentVersion require(Long versionId) {
        return repository.findById(versionId).orElseThrow(() -> new NotFoundException("Версия документа не найдена: " + versionId));
    }

    public Resource load(Long versionId, String format) {
        DocumentVersion v = require(versionId);
        String path = "pdf".equalsIgnoreCase(format) ? v.getPdfPath() : "docx".equalsIgnoreCase(format) ? v.getDocxPath() : null;
        if (path == null || path.isBlank()) throw new BusinessException("Файл этой версии в выбранном формате отсутствует");
        return storage.load(path);
    }

    private String normalizeType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!type.equals("CONTRACT") && !type.equals("SPECIFICATION")) throw new BusinessException("Тип документа должен быть CONTRACT или SPECIFICATION");
        return type;
    }

    private DocumentWorkflowDtos.VersionResponse dto(DocumentVersion v) {
        AppUser u = v.getCreatedBy();
        String prefix = "/api/document-versions/" + v.getId() + "/download/";
        return new DocumentWorkflowDtos.VersionResponse(v.getId(), v.getVersionNumber(), v.getNote(),
                v.getDocxPath() == null ? null : prefix + "docx", v.getPdfPath() == null ? null : prefix + "pdf",
                u == null ? null : u.getId(), u == null ? "Система" : u.getFullName(), v.getCreatedAt());
    }
}
