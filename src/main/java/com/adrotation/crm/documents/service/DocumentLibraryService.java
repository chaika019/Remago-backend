package com.adrotation.crm.documents.service;

import com.adrotation.crm.company.entity.Company;
import com.adrotation.crm.company.repository.CompanyRepository;
import com.adrotation.crm.crm.entity.Deal;
import com.adrotation.crm.crm.repository.DealRepository;
import com.adrotation.crm.documents.dto.DocumentLibraryDtos;
import com.adrotation.crm.documents.entity.Contract;
import com.adrotation.crm.documents.entity.Specification;
import com.adrotation.crm.documents.entity.StoredDocument;
import com.adrotation.crm.documents.enums.DocumentLibrarySource;
import com.adrotation.crm.documents.enums.DocumentLibraryType;
import com.adrotation.crm.documents.repository.ContractRepository;
import com.adrotation.crm.documents.repository.SpecificationRepository;
import com.adrotation.crm.documents.repository.StoredDocumentRepository;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.shared.exception.NotFoundException;
import com.adrotation.crm.shared.service.TextSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentLibraryService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".docx", ".doc", ".xlsx", ".xls", ".png", ".jpg", ".jpeg"
    );

    private final SpecificationRepository specificationRepository;
    private final ContractRepository contractRepository;
    private final StoredDocumentRepository storedDocumentRepository;
    private final CompanyRepository companyRepository;
    private final DealRepository dealRepository;
    private final LocalDocumentStorageService storage;
    private final TextSanitizer sanitizer;

    @Transactional(readOnly = true)
    public DocumentLibraryDtos.PageResponse list(
            DocumentLibraryType type,
            String query,
            Long companyId,
            LocalDate dateFrom,
            LocalDate dateTo,
            int page,
            int size
    ) {
        validateDateRange(dateFrom, dateTo);

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        List<DocumentLibraryDtos.DocumentListItem> all = new ArrayList<>();

        if (type == null || type == DocumentLibraryType.SPECIFICATION) {
            specificationRepository.findAll()
                    .stream()
                    .map(this::fromSpecification)
                    .forEach(all::add);
        }

        if (type == null || type == DocumentLibraryType.CONTRACT) {
            contractRepository.findAll()
                    .stream()
                    .map(this::fromContract)
                    .forEach(all::add);
        }

        storedDocumentRepository.findAll()
                .stream()
                .filter(document -> type == null || document.getDocumentType() == type)
                .map(this::fromUploaded)
                .forEach(all::add);

        String normalizedQuery = normalize(query);

        List<DocumentLibraryDtos.DocumentListItem> filtered = all.stream()
                .filter(item -> companyId == null || companyId.equals(item.companyId()))
                .filter(item -> matchesDateRange(item.documentDate(), dateFrom, dateTo))
                .filter(item -> normalizedQuery.isBlank() || matches(item, normalizedQuery))
                .sorted(documentComparator())
                .toList();

        int totalElements = filtered.size();
        long offset = (long) safePage * safeSize;
        int fromIndex = (int) Math.min(offset, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);
        int totalPages = totalElements == 0
                ? 0
                : (int) Math.ceil(totalElements / (double) safeSize);

        return new DocumentLibraryDtos.PageResponse(
                List.copyOf(filtered.subList(fromIndex, toIndex)),
                totalElements,
                safePage,
                safeSize,
                totalPages
        );
    }

    @Transactional(readOnly = true)
    public DocumentLibraryDtos.SummaryResponse summary() {
        long generatedSpecifications = specificationRepository.count();
        long generatedContracts = contractRepository.count();

        List<StoredDocument> uploaded = storedDocumentRepository.findAll();

        long specifications = generatedSpecifications
                + count(uploaded, DocumentLibraryType.SPECIFICATION);
        long contracts = generatedContracts
                + count(uploaded, DocumentLibraryType.CONTRACT);
        long invoices = count(uploaded, DocumentLibraryType.INVOICE);
        long acts = count(uploaded, DocumentLibraryType.ACT);
        long additionalAgreements = count(uploaded, DocumentLibraryType.ADDITIONAL_AGREEMENT);
        long other = count(uploaded, DocumentLibraryType.OTHER);

        return new DocumentLibraryDtos.SummaryResponse(
                specifications + contracts + invoices + acts + additionalAgreements + other,
                specifications,
                contracts,
                invoices,
                acts,
                additionalAgreements,
                other
        );
    }

    @Transactional
    public DocumentLibraryDtos.UploadResponse upload(
            MultipartFile file,
            DocumentLibraryType type,
            String title,
            String number,
            LocalDate documentDate,
            Long companyId,
            Long dealId,
            String notes
    ) {
        validateUpload(file, type, title);

        String cleanTitle = sanitizer.plain(title).trim();
        Company company = resolveCompany(companyId);
        Deal deal = resolveDeal(dealId);

        if (deal != null) {
            if (company != null && !deal.getCompany().getId().equals(company.getId())) {
                throw new BusinessException("Сделка относится к другой компании");
            }
            if (company == null) {
                company = deal.getCompany();
            }
        }

        String originalName = safeOriginalFileName(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        if (!extension.isBlank() && !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(
                    "Неподдерживаемый формат файла. Разрешены PDF, DOC/DOCX, XLS/XLSX, PNG и JPG"
            );
        }

        String storedName = "uploaded-" + UUID.randomUUID() + extension;
        String storedPath = null;

        try {
            storedPath = storage.save(storedName, file.getBytes());

            StoredDocument document = new StoredDocument();
            document.setDocumentType(type);
            document.setTitle(cleanTitle);
            document.setNumber(blankToNull(number));
            document.setDocumentDate(documentDate);
            document.setCompany(company);
            document.setDeal(deal);
            document.setOriginalFileName(originalName);
            document.setStoredPath(storedPath);
            document.setMimeType(blankToNull(file.getContentType()));
            document.setNotes(blankToNull(notes));

            return toUploadResponse(storedDocumentRepository.save(document));
        } catch (IOException e) {
            storage.delete(storedPath);
            throw new BusinessException("Не удалось прочитать загружаемый файл: " + e.getMessage());
        } catch (RuntimeException e) {
            storage.delete(storedPath);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public StoredDocument requireUploaded(Long id) {
        return storedDocumentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Документ не найден: " + id));
    }

    @Transactional(readOnly = true)
    public Resource loadUploaded(Long id) {
        StoredDocument document = requireUploaded(id);
        return storage.load(document.getStoredPath());
    }

    private Company resolveCompany(Long companyId) {
        if (companyId == null) {
            return null;
        }
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("Компания не найдена: " + companyId));
    }

    private Deal resolveDeal(Long dealId) {
        if (dealId == null) {
            return null;
        }
        return dealRepository.findById(dealId)
                .orElseThrow(() -> new NotFoundException("CRM-карточка не найдена: " + dealId));
    }

    private DocumentLibraryDtos.DocumentListItem fromSpecification(Specification specification) {
        List<DocumentLibraryDtos.FileLink> files = new ArrayList<>();

        if (nonBlank(specification.getDocxPath())) {
            files.add(new DocumentLibraryDtos.FileLink(
                    "DOCX",
                    "Спецификация-" + specification.getNumber() + ".docx",
                    "/api/specifications/" + specification.getId() + "/download/docx"
            ));
        }

        if (nonBlank(specification.getPdfPath())) {
            files.add(new DocumentLibraryDtos.FileLink(
                    "PDF",
                    "Спецификация-" + specification.getNumber() + ".pdf",
                    "/api/specifications/" + specification.getId() + "/download/pdf"
            ));
        }

        return new DocumentLibraryDtos.DocumentListItem(
                specification.getId(),
                DocumentLibrarySource.SPECIFICATION,
                DocumentLibraryType.SPECIFICATION,
                "Спецификация № " + specification.getNumber(),
                String.valueOf(specification.getNumber()),
                specification.getSpecificationDate(),
                specification.getCompany().getId(),
                specification.getCompany().getCanonicalName(),
                specification.getDeal() == null ? null : specification.getDeal().getId(),
                specification.getContract() == null ? null : specification.getContract().getId(),
                specification.getStatus().name(),
                specification.getTotalAfterDiscount(),
                List.copyOf(files),
                specification.getCreatedAt()
        );
    }

    private DocumentLibraryDtos.DocumentListItem fromContract(Contract contract) {
        List<DocumentLibraryDtos.FileLink> files = new ArrayList<>();

        if (nonBlank(contract.getDocxPath())) {
            files.add(new DocumentLibraryDtos.FileLink(
                    "DOCX",
                    "Договор-" + contract.getNumber() + ".docx",
                    "/api/contracts/" + contract.getId() + "/download/docx"
            ));
        }

        if (nonBlank(contract.getPdfPath())) {
            files.add(new DocumentLibraryDtos.FileLink(
                    "PDF",
                    "Договор-" + contract.getNumber() + ".pdf",
                    "/api/contracts/" + contract.getId() + "/download/pdf"
            ));
        }

        return new DocumentLibraryDtos.DocumentListItem(
                contract.getId(),
                DocumentLibrarySource.CONTRACT,
                DocumentLibraryType.CONTRACT,
                "Договор № " + contract.getNumber(),
                contract.getNumber(),
                contract.getContractDate(),
                contract.getCompany().getId(),
                contract.getCompany().getCanonicalName(),
                null,
                contract.getId(),
                contract.getDocumentStatus().name(),
                null,
                List.copyOf(files),
                contract.getCreatedAt()
        );
    }

    private DocumentLibraryDtos.DocumentListItem fromUploaded(StoredDocument document) {
        return new DocumentLibraryDtos.DocumentListItem(
                document.getId(),
                DocumentLibrarySource.UPLOADED,
                document.getDocumentType(),
                document.getTitle(),
                document.getNumber(),
                document.getDocumentDate(),
                document.getCompany() == null ? null : document.getCompany().getId(),
                document.getCompany() == null ? null : document.getCompany().getCanonicalName(),
                document.getDeal() == null ? null : document.getDeal().getId(),
                null,
                "STORED",
                null,
                List.of(new DocumentLibraryDtos.FileLink(
                        fileFormat(document.getOriginalFileName()),
                        document.getOriginalFileName(),
                        "/api/document-library/uploaded/" + document.getId() + "/download"
                )),
                document.getCreatedAt()
        );
    }

    private DocumentLibraryDtos.UploadResponse toUploadResponse(StoredDocument document) {
        return new DocumentLibraryDtos.UploadResponse(
                document.getId(),
                document.getDocumentType(),
                document.getTitle(),
                document.getNumber(),
                document.getDocumentDate(),
                document.getCompany() == null ? null : document.getCompany().getId(),
                document.getDeal() == null ? null : document.getDeal().getId(),
                document.getOriginalFileName(),
                "/api/document-library/uploaded/" + document.getId() + "/download",
                document.getCreatedAt()
        );
    }

    private void validateUpload(MultipartFile file, DocumentLibraryType type, String title) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Выберите файл документа");
        }
        if (type == null) {
            throw new BusinessException("Выберите тип документа");
        }

        String cleanTitle = sanitizer.plain(title == null ? "" : title);
        if (cleanTitle == null || cleanTitle.isBlank()) {
            throw new BusinessException("Укажите название документа");
        }
    }

    private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BusinessException("Дата начала периода не может быть позже даты окончания");
        }
    }

    private boolean matchesDateRange(LocalDate date, LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null && dateTo == null) {
            return true;
        }
        if (date == null) {
            return false;
        }
        return (dateFrom == null || !date.isBefore(dateFrom))
                && (dateTo == null || !date.isAfter(dateTo));
    }

    private boolean matches(DocumentLibraryDtos.DocumentListItem item, String query) {
        return normalize(item.title()).contains(query)
                || normalize(item.number()).contains(query)
                || normalize(item.companyName()).contains(query)
                || normalize(item.status()).contains(query)
                || normalize(item.type().name()).contains(query);
    }

    private Comparator<DocumentLibraryDtos.DocumentListItem> documentComparator() {
        return Comparator.comparing(
                        DocumentLibraryDtos.DocumentListItem::documentDate,
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(
                        DocumentLibraryDtos.DocumentListItem::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(
                        DocumentLibraryDtos.DocumentListItem::id,
                        Comparator.nullsLast(Comparator.reverseOrder())
                );
    }

    private long count(List<StoredDocument> documents, DocumentLibraryType type) {
        return documents.stream()
                .filter(document -> document.getDocumentType() == type)
                .count();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }

        String sanitized = sanitizer.plain(value);
        return sanitized == null || sanitized.isBlank() ? null : sanitized.trim();
    }

    private String safeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "document";
        }

        try {
            Path fileName = Path.of(originalFileName).getFileName();
            return fileName == null || fileName.toString().isBlank()
                    ? "document"
                    : fileName.toString();
        } catch (RuntimeException ignored) {
            return "document";
        }
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }

        String extension = fileName.substring(dot).toLowerCase(Locale.ROOT);
        return extension.length() <= 12 && extension.matches("\\.[a-z0-9]+")
                ? extension
                : "";
    }

    private String fileFormat(String fileName) {
        String extension = extensionOf(fileName);
        return extension.isBlank()
                ? "FILE"
                : extension.substring(1).toUpperCase(Locale.ROOT);
    }

    private boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
