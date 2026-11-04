package com.adrotation.crm.documents.service;

import com.adrotation.crm.audit.service.AuditService;
import com.adrotation.crm.company.entity.Company;
import com.adrotation.crm.company.service.CompanyService;
import com.adrotation.crm.documents.dto.SpecificationDtos;
import com.adrotation.crm.documents.entity.Contract;
import com.adrotation.crm.documents.entity.OrganizationProfile;
import com.adrotation.crm.documents.repository.ContractRepository;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.shared.exception.NotFoundException;
import com.adrotation.crm.shared.service.TextSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository repository;
    private final CompanyService companyService;
    private final OrganizationProfileService organizationProfileService;
    private final ContractTemplateService templateService;
    private final GotenbergDocumentConverter pdfConverter;
    private final LocalDocumentStorageService storage;
    private final AuditService auditService;
    private final DocumentVersionService versionService;
    private final TextSanitizer sanitizer;

    @Value("${app.time-zone:Europe/Minsk}")
    private String timeZone;

    @Transactional(readOnly = true)
    public Contract require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Договор не найден: " + id));
    }

    @Transactional(readOnly = true)
    public Contract activeForCompany(Long companyId) {
        LocalDate today = LocalDate.now(ZoneId.of(timeZone));

        return repository.findByCompany_IdAndActiveTrueOrderByContractDateDesc(companyId)
                .stream()
                .filter(contract -> isEffective(contract, today))
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public SpecificationDtos.ContractResponse create(SpecificationDtos.ContractRequest request) {
        String number = sanitizer.plain(request.number());
        if (number == null || number.isBlank()) {
            throw new BusinessException("Укажите номер договора");
        }
        number = number.trim();

        Long companyId = request.companyId();
        if (repository.existsByCompany_IdAndNumberIgnoreCase(companyId, number)) {
            throw new BusinessException("Договор № " + number + " уже зарегистрирован у этой компании");
        }

        if (request.validUntil() != null && request.validUntil().isBefore(request.contractDate())) {
            throw new BusinessException("Дата окончания договора не может быть раньше даты договора");
        }

        Company company = companyService.require(companyId);
        OrganizationProfile executor = organizationProfileService.require();

        Contract contract = new Contract();
        contract.setCompany(company);
        contract.setNumber(number);
        contract.setContractDate(request.contractDate());
        contract.setValidUntil(request.validUntil());
        contract.setAutoRenewal(request.autoRenewal() == null || request.autoRenewal());
        contract.setActive(request.active() == null || request.active());

        contract = repository.save(contract);

        String safeNumber = number.replaceAll("[^a-zA-Z0-9а-яА-Я_-]+", "-");
        String baseName = "contract-" + safeNumber + "-company-" + company.getId();
        String docxPath = null;
        String pdfPath = null;

        try {
            byte[] docx = templateService.render(executor, contract);
            docxPath = storage.save(baseName + ".docx", docx);
            contract.setDocxPath(docxPath);

            try {
                byte[] pdf = pdfConverter.docxToPdf(baseName + ".docx", docx);
                if (pdf != null && pdf.length > 0) {
                    pdfPath = storage.save(baseName + ".pdf", pdf);
                    contract.setPdfPath(pdfPath);
                }
            } catch (RuntimeException ignored) {
                // DOCX is still a valid generated contract. PDF can be regenerated later.
            }

            contract = repository.save(contract);
            versionService.capture("CONTRACT", contract.getId(), contract.getDocxPath(), contract.getPdfPath(), "Первичная генерация");
            auditService.record("CONTRACT", contract.getId(), "generated", null, "№" + contract.getNumber());
            return toDto(contract);
        } catch (RuntimeException e) {
            storage.delete(docxPath);
            storage.delete(pdfPath);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<SpecificationDtos.ContractResponse> list(Long companyId) {
        companyService.require(companyId);
        return repository.findByCompany_IdOrderByContractDateDesc(companyId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public SpecificationDtos.ContractResponse toDto(Contract contract) {
        return new SpecificationDtos.ContractResponse(
                contract.getId(),
                contract.getCompany().getId(),
                contract.getNumber(),
                contract.getContractDate(),
                contract.getValidUntil(),
                contract.isAutoRenewal(),
                contract.isActive(),
                contract.getDocumentStatus(),
                contract.getDocxPath() == null ? null : "/api/contracts/" + contract.getId() + "/download/docx",
                contract.getPdfPath() == null ? null : "/api/contracts/" + contract.getId() + "/download/pdf"
        );
    }

    private boolean isEffective(Contract contract, LocalDate today) {
        if (contract.getContractDate().isAfter(today)) {
            return false;
        }

        if (contract.isAutoRenewal()) {
            return true;
        }

        return contract.getValidUntil() == null || !contract.getValidUntil().isBefore(today);
    }
}
