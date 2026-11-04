package com.adrotation.crm.company.workspace;

import com.adrotation.crm.audit.dto.AuditLogDto;
import com.adrotation.crm.audit.entity.AuditLog;
import com.adrotation.crm.audit.repository.AuditLogRepository;
import com.adrotation.crm.auth.security.CurrentUserService;
import com.adrotation.crm.company.entity.Company;
import com.adrotation.crm.company.repository.ContactRepository;
import com.adrotation.crm.company.service.CompanyService;
import com.adrotation.crm.crm.entity.Deal;
import com.adrotation.crm.crm.repository.DealRepository;
import com.adrotation.crm.documents.entity.Contract;
import com.adrotation.crm.documents.entity.Specification;
import com.adrotation.crm.documents.repository.ContractRepository;
import com.adrotation.crm.documents.repository.SpecificationRepository;
import com.adrotation.crm.shared.service.DtoMapper;
import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.user.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CompanyWorkspaceService {
    private final CompanyService companyService;
    private final ContactRepository contactRepository;
    private final DealRepository dealRepository;
    private final ContractRepository contractRepository;
    private final SpecificationRepository specificationRepository;
    private final AuditLogRepository auditLogRepository;
    private final CurrentUserService currentUserService;
    private final DtoMapper mapper;

    @Transactional(readOnly = true)
    public CompanyWorkspaceDtos.Response get(Long companyId) {
        AppUser current = currentUserService.requireCurrentUser();
        Company company = companyService.require(companyId);
        List<Deal> companyDeals = dealRepository.findByCompany_IdOrderByActivityDateDesc(companyId);
        boolean crmVisible = canViewCrm(current);
        boolean docsVisible = canViewDocuments(current);
        if (isManagerOnly(current) && companyDeals.stream().noneMatch(d -> d.getCreatedBy().getId().equals(current.getId()))
                && (company.getCreatedBy() == null || !company.getCreatedBy().getId().equals(current.getId()))) {
            throw new AccessDeniedException("Нет доступа к карточке этой компании");
        }

        List<Deal> visibleDeals = !crmVisible ? List.of() : isManagerOnly(current)
                ? companyDeals.stream().filter(d -> d.getCreatedBy().getId().equals(current.getId())).toList()
                : companyDeals;

        List<Contract> contracts = docsVisible ? contractRepository.findByCompany_IdOrderByContractDateDesc(companyId) : List.of();
        List<Specification> specs = docsVisible ? specificationRepository.findByCompany_IdOrderBySpecificationDateDesc(companyId) : List.of();
        List<CompanyWorkspaceDtos.DocumentSummary> documents = new ArrayList<>();
        contracts.forEach(c -> documents.add(new CompanyWorkspaceDtos.DocumentSummary("CONTRACT", c.getId(), c.getNumber(), c.getContractDate(), c.getDocumentStatus(), null,
                c.getDocxPath() == null ? null : "/api/contracts/" + c.getId() + "/download/docx",
                c.getPdfPath() == null ? null : "/api/contracts/" + c.getId() + "/download/pdf")));
        specs.forEach(s -> documents.add(new CompanyWorkspaceDtos.DocumentSummary("SPECIFICATION", s.getId(), String.valueOf(s.getNumber()), s.getSpecificationDate(), s.getStatus(), s.getTotalAfterDiscount(),
                s.getDocxPath() == null ? null : "/api/specifications/" + s.getId() + "/download/docx",
                s.getPdfPath() == null ? null : "/api/specifications/" + s.getId() + "/download/pdf")));
        documents.sort(Comparator.comparing(CompanyWorkspaceDtos.DocumentSummary::date, Comparator.nullsLast(Comparator.reverseOrder())));

        List<AuditLog> timeline = new ArrayList<>(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("COMPANY", companyId));
        if (crmVisible && !visibleDeals.isEmpty()) timeline.addAll(auditLogRepository.findByEntityTypeAndEntityIdInOrderByCreatedAtDesc("DEAL", visibleDeals.stream().map(Deal::getId).toList()));
        if (docsVisible) {
            if (!contracts.isEmpty()) timeline.addAll(auditLogRepository.findByEntityTypeAndEntityIdInOrderByCreatedAtDesc("CONTRACT", contracts.stream().map(Contract::getId).toList()));
            if (!specs.isEmpty()) timeline.addAll(auditLogRepository.findByEntityTypeAndEntityIdInOrderByCreatedAtDesc("SPECIFICATION", specs.stream().map(Specification::getId).toList()));
        }
        timeline.sort(Comparator.comparing(AuditLog::getCreatedAt).reversed());
        List<AuditLogDto> timelineDtos = timeline.stream().limit(200).map(mapper::audit).toList();

        return new CompanyWorkspaceDtos.Response(mapper.company(company),
                contactRepository.findByCompany_IdOrderByFioAsc(companyId).stream().map(mapper::contact).toList(),
                visibleDeals.stream().map(mapper::deal).toList(), docsVisible, documents, timelineDtos);
    }

    private boolean canViewDocuments(AppUser u) { return u.getRoles().stream().anyMatch(r -> r == Role.SUPER_ADMIN || r == Role.BUH || r == Role.DELO); }
    private boolean canViewCrm(AppUser u) { return u.getRoles().stream().anyMatch(r -> r == Role.SUPER_ADMIN || r == Role.DIRECTOR || r == Role.ROP || r == Role.ROK || r == Role.MANAGER); }
    private boolean isManagerOnly(AppUser u) { return u.getRoles().contains(Role.MANAGER) && u.getRoles().stream().noneMatch(r -> r == Role.SUPER_ADMIN || r == Role.DIRECTOR || r == Role.ROP || r == Role.ROK); }
}
