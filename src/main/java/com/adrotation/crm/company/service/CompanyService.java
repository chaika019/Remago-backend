package com.adrotation.crm.company.service;

import com.adrotation.crm.auth.security.CurrentUserService;
import com.adrotation.crm.audit.service.AuditService;
import com.adrotation.crm.company.dto.CompanyDtos;
import com.adrotation.crm.company.entity.Company;
import com.adrotation.crm.company.entity.Contact;
import com.adrotation.crm.company.enums.ClientStatus;
import com.adrotation.crm.company.repository.CompanyRepository;
import com.adrotation.crm.company.repository.ContactRepository;
import com.adrotation.crm.shared.exception.NotFoundException;
import com.adrotation.crm.shared.service.DtoMapper;
import com.adrotation.crm.shared.service.TextSanitizer;
import com.adrotation.crm.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final ContactRepository contactRepository;
    private final CompanyNormalizationService normalizationService;
    private final CurrentUserService currentUserService;
    private final TextSanitizer sanitizer;
    private final DtoMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Company require(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Компания не найдена: " + id));
    }

    @Transactional(readOnly = true)
    public CompanyDtos.CompanyResponse get(Long id) {
        return mapper.company(require(id));
    }

    @Transactional(readOnly = true)
    public List<CompanyDtos.CompanyResponse> search(String query) {
        String normalizedQuery = sanitizeNullable(query);
        if (normalizedQuery == null) {
            normalizedQuery = "";
        }

        return companyRepository
                .findTop20ByCanonicalNameContainingIgnoreCaseOrderByCanonicalNameAsc(normalizedQuery)
                .stream()
                .map(mapper::company)
                .toList();
    }

    @Transactional
    public CompanyDtos.CompanyResponse create(CompanyDtos.CompanyCreateRequest request) {
        AppUser author = currentUserService.requireCurrentUser();
        var normalized = normalizationService.normalizeAndCheck(
                request.legalForm(),
                request.name(),
                request.inn(),
                null
        );

        Company company = new Company();
        company.setLegalForm(request.legalForm());
        company.setShortName(normalized.shortName());
        company.setCanonicalName(normalized.canonicalName());
        company.setInn(normalized.inn());
        company.setStatus(request.status() == null ? ClientStatus.NEW : request.status());
        company.setLegalAddress(sanitizeNullable(request.legalAddress()));
        company.setPostalAddress(sanitizeNullable(request.postalAddress()));
        company.setSettlementAccount(normalizeAccount(request.settlementAccount()));
        company.setBankName(sanitizeNullable(request.bankName()));
        company.setBankBic(normalizeBankBic(request.bankBic()));
        company.setCreatedBy(author);

        company = companyRepository.save(company);
        auditService.record("COMPANY", company.getId(), "created", null, company.getCanonicalName());
        return mapper.company(company);
    }

    @Transactional
    public CompanyDtos.CompanyResponse update(Long id, CompanyDtos.CompanyUpdateRequest request) {
        Company company = require(id);
        String oldCanonicalName = company.getCanonicalName();
        String oldInn = company.getInn();
        ClientStatus oldStatus = company.getStatus();

        var legalForm = request.legalForm() == null
                ? company.getLegalForm()
                : request.legalForm();

        String rawName = request.name() == null
                ? company.getShortName()
                : request.name();

        String requestedInn = Boolean.TRUE.equals(request.clearInn())
                ? null
                : request.inn() == null ? company.getInn() : request.inn();

        var normalized = normalizationService.normalizeAndCheck(
                legalForm,
                rawName,
                requestedInn,
                id
        );

        company.setLegalForm(legalForm);
        company.setShortName(normalized.shortName());
        company.setCanonicalName(normalized.canonicalName());
        company.setInn(normalized.inn());

        if (request.status() != null) {
            company.setStatus(request.status());
        }
        if (request.legalAddress() != null) {
            company.setLegalAddress(sanitizeNullable(request.legalAddress()));
        }
        if (request.postalAddress() != null) {
            company.setPostalAddress(sanitizeNullable(request.postalAddress()));
        }
        if (request.settlementAccount() != null) {
            company.setSettlementAccount(normalizeAccount(request.settlementAccount()));
        }
        if (request.bankName() != null) {
            company.setBankName(sanitizeNullable(request.bankName()));
        }
        if (request.bankBic() != null) {
            company.setBankBic(normalizeBankBic(request.bankBic()));
        }

        company = companyRepository.save(company);
        auditService.record("COMPANY", id, "canonicalName", oldCanonicalName, company.getCanonicalName());
        auditService.record("COMPANY", id, "unp", oldInn, company.getInn());
        auditService.record("COMPANY", id, "status", oldStatus, company.getStatus());
        return mapper.company(company);
    }

    @Transactional
    public CompanyDtos.CompanyResponse updateStatus(Long id, ClientStatus status) {
        Company company = require(id);
        ClientStatus old = company.getStatus();
        company.setStatus(status);
        company = companyRepository.save(company);
        auditService.record("COMPANY", id, "status", old, status);
        return mapper.company(company);
    }

    @Transactional(readOnly = true)
    public List<CompanyDtos.ContactResponse> contacts(Long companyId) {
        require(companyId);
        return contactRepository.findByCompany_IdOrderByFioAsc(companyId)
                .stream()
                .map(mapper::contact)
                .toList();
    }

    @Transactional
    public CompanyDtos.ContactResponse addContact(
            Long companyId,
            CompanyDtos.ContactRequest request
    ) {
        Contact contact = new Contact();
        contact.setCompany(require(companyId));
        applyContact(contact, request);
        contact = contactRepository.save(contact);
        auditService.record("COMPANY", companyId, "contactAdded", null, contact.getFio());
        return mapper.contact(contact);
    }

    @Transactional
    public CompanyDtos.ContactResponse updateContact(
            Long contactId,
            CompanyDtos.ContactRequest request
    ) {
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new NotFoundException("Контакт не найден: " + contactId));

        String oldFio = contact.getFio();
        applyContact(contact, request);
        contact = contactRepository.save(contact);
        auditService.record("COMPANY", contact.getCompany().getId(), "contactUpdated", oldFio, contact.getFio());
        return mapper.contact(contact);
    }

    @Transactional
    public void deleteContact(Long contactId) {
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new NotFoundException("Контакт не найден: " + contactId));
        Long companyId = contact.getCompany().getId();
        String fio = contact.getFio();
        contactRepository.delete(contact);
        auditService.record("COMPANY", companyId, "contactDeleted", fio, null);
    }

    private void applyContact(Contact contact, CompanyDtos.ContactRequest request) {
        contact.setFio(sanitizeNullable(request.fio()));
        contact.setPosition(sanitizeNullable(request.position()));
        contact.setPhone(sanitizeNullable(request.phone()));
        contact.setEmail(normalizeEmail(request.email()));
        contact.setTelegramChatId(sanitizeNullable(request.telegramChatId()));
        contact.setViberUserId(sanitizeNullable(request.viberUserId()));
        contact.setPreferredDeliveryChannel(request.preferredDeliveryChannel());
    }

    private String sanitizeNullable(String value) {
        String sanitized = sanitizer.plain(value);
        return sanitized == null || sanitized.isBlank() ? null : sanitized;
    }

    private String normalizeAccount(String value) {
        String sanitized = sanitizeNullable(value);
        return sanitized == null
                ? null
                : sanitized.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private String normalizeBankBic(String value) {
        String sanitized = sanitizeNullable(value);
        return sanitized == null
                ? null
                : sanitized.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private String normalizeEmail(String value) {
        String sanitized = sanitizeNullable(value);
        return sanitized == null ? null : sanitized.toLowerCase(Locale.ROOT);
    }
}
