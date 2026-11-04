package com.adrotation.crm.documents.service;

import com.adrotation.crm.audit.service.AuditService;
import com.adrotation.crm.documents.dto.DocumentWorkflowDtos;
import com.adrotation.crm.documents.entity.Contract;
import com.adrotation.crm.documents.entity.Specification;
import com.adrotation.crm.documents.enums.DocumentStatus;
import com.adrotation.crm.documents.repository.ContractRepository;
import com.adrotation.crm.documents.repository.SpecificationRepository;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DocumentWorkflowService {
    private final ContractRepository contractRepository;
    private final SpecificationRepository specificationRepository;
    private final AuditService auditService;

    private static final Map<DocumentStatus, Set<DocumentStatus>> ALLOWED = Map.of(
            DocumentStatus.DRAFT, EnumSet.of(DocumentStatus.GENERATED, DocumentStatus.ARCHIVED),
            DocumentStatus.GENERATED, EnumSet.of(DocumentStatus.REVIEW, DocumentStatus.SIGNED, DocumentStatus.ARCHIVED, DocumentStatus.FAILED),
            DocumentStatus.PARTIALLY_GENERATED, EnumSet.of(DocumentStatus.GENERATED, DocumentStatus.REVIEW, DocumentStatus.FAILED, DocumentStatus.ARCHIVED),
            DocumentStatus.REVIEW, EnumSet.of(DocumentStatus.APPROVED, DocumentStatus.GENERATED, DocumentStatus.ARCHIVED),
            DocumentStatus.APPROVED, EnumSet.of(DocumentStatus.SIGNED, DocumentStatus.REVIEW, DocumentStatus.ARCHIVED),
            DocumentStatus.SIGNED, EnumSet.of(DocumentStatus.SENT, DocumentStatus.ARCHIVED),
            DocumentStatus.SENT, EnumSet.of(DocumentStatus.ARCHIVED),
            DocumentStatus.FAILED, EnumSet.of(DocumentStatus.GENERATED, DocumentStatus.ARCHIVED),
            DocumentStatus.ARCHIVED, EnumSet.noneOf(DocumentStatus.class)
    );

    @Transactional
    public DocumentWorkflowDtos.StatusResponse contract(Long id, DocumentStatus target) {
        Contract c = contractRepository.findById(id).orElseThrow(() -> new NotFoundException("Договор не найден: " + id));
        validate(c.getDocumentStatus(), target);
        DocumentStatus old = c.getDocumentStatus();
        c.setDocumentStatus(target);
        contractRepository.save(c);
        auditService.record("CONTRACT", id, "status", old, target);
        return new DocumentWorkflowDtos.StatusResponse("CONTRACT", id, target);
    }

    @Transactional
    public DocumentWorkflowDtos.StatusResponse specification(Long id, DocumentStatus target) {
        Specification s = specificationRepository.findById(id).orElseThrow(() -> new NotFoundException("Спецификация не найдена: " + id));
        validate(s.getStatus(), target);
        DocumentStatus old = s.getStatus();
        s.setStatus(target);
        specificationRepository.save(s);
        auditService.record("SPECIFICATION", id, "status", old, target);
        return new DocumentWorkflowDtos.StatusResponse("SPECIFICATION", id, target);
    }

    private void validate(DocumentStatus old, DocumentStatus target) {
        if (old == target) return;
        if (!ALLOWED.getOrDefault(old, Set.of()).contains(target)) {
            throw new BusinessException("Недопустимый переход статуса: " + old + " → " + target);
        }
    }
}
