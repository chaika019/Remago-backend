package com.adrotation.crm.documents.importing;

import com.adrotation.crm.auth.security.CurrentUserService;
import com.adrotation.crm.company.entity.Company;
import com.adrotation.crm.company.enums.ClientStatus;
import com.adrotation.crm.company.enums.LegalForm;
import com.adrotation.crm.company.repository.CompanyRepository;
import com.adrotation.crm.documents.entity.*;
import com.adrotation.crm.documents.enums.DocumentStatus;
import com.adrotation.crm.documents.repository.*;
import com.adrotation.crm.documents.service.DocumentSequenceService;
import com.adrotation.crm.documents.service.DocumentVersionService;
import com.adrotation.crm.documents.service.LocalDocumentStorageService;
import com.adrotation.crm.shared.drive.DriveStorageService;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.user.entity.AppUser;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class HistoryImportService {
    private static final Pattern DRIVE_ID = Pattern.compile("(?:/d/|/folders/|id=)([A-Za-z0-9_-]{15,})");

    private final HistorySpreadsheetParser parser;
    private final CompanyRepository companyRepository;
    private final ContractRepository contractRepository;
    private final SpecificationRepository specificationRepository;
    private final ImportBatchRepository importBatchRepository;
    private final CurrentUserService currentUserService;
    private final DocumentSequenceService sequenceService;
    private final DocumentVersionService versionService;
    private final LocalDocumentStorageService storage;
    private final DriveStorageService driveStorage;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    public HistoryImportDtos.PreviewResponse preview(MultipartFile file) {
        try { return previewBytes(file.getOriginalFilename(), file.getBytes()); }
        catch (Exception ex) { if (ex instanceof BusinessException b) throw b; throw new BusinessException("Не удалось прочитать файл импорта"); }
    }

    public HistoryImportDtos.PreviewResponse previewDrive(String urlOrFileId) {
        DriveStorageService.DownloadResult source = downloadDrive(urlOrFileId);
        return previewBytes(source.fileName(), source.bytes());
    }

    public HistoryImportDtos.CommitResponse commit(MultipartFile file) {
        try { return commitBytes("UPLOAD", file.getOriginalFilename(), file.getBytes()); }
        catch (Exception ex) { if (ex instanceof BusinessException b) throw b; throw new BusinessException("Не удалось импортировать файл: " + ex.getMessage()); }
    }

    public HistoryImportDtos.CommitResponse commitDrive(String urlOrFileId) {
        DriveStorageService.DownloadResult source = downloadDrive(urlOrFileId);
        return commitBytes("GOOGLE_DRIVE", source.fileName(), source.bytes());
    }

    private HistoryImportDtos.PreviewResponse previewBytes(String sourceName, byte[] bytes) {
        List<HistorySpreadsheetParser.RowData> rows = parser.parse(bytes);
        List<HistoryImportDtos.PreviewRow> preview = rows.stream().map(this::previewRow).toList();
        int duplicates = (int) preview.stream().filter(r -> "SKIP_DUPLICATE".equals(r.action())).count();
        int warnings = (int) preview.stream().filter(r -> !r.warnings().isEmpty()).count();
        int creatable = (int) preview.stream().filter(r -> "CREATE".equals(r.action()) || "USE_EXISTING_COMPANY".equals(r.action())).count();
        return new HistoryImportDtos.PreviewResponse(sourceName == null ? "import.xlsx" : sourceName, preview.size(), creatable, duplicates, warnings, preview);
    }

    private HistoryImportDtos.PreviewRow previewRow(HistorySpreadsheetParser.RowData row) {
        List<String> warnings = new ArrayList<>();
        if ("UNKNOWN".equals(row.documentType())) warnings.add("Не удалось определить тип документа");
        if (row.number() == null || row.number().isBlank()) warnings.add("Не указан номер документа");
        if (row.documentDate() == null) warnings.add("Не распознана историческая дата");
        Optional<Company> existing = findCompany(row);
        String action = existing.isPresent() ? "USE_EXISTING_COMPANY" : "CREATE";
        if (existing.isPresent() && isDuplicate(row, existing.get())) action = "SKIP_DUPLICATE";
        if (!warnings.isEmpty() && (row.number() == null || row.documentDate() == null || "UNKNOWN".equals(row.documentType()))) action = "REVIEW";
        return new HistoryImportDtos.PreviewRow(row.rowNumber(), row.documentType(), row.rawCompanyName(), row.legalForm(), row.unp(), row.number(),
                row.documentDate(), row.contractNumber(), row.contractDate(), row.amount(), row.linkedFileUrl(), action, warnings);
    }

    private HistoryImportDtos.CommitResponse commitBytes(String sourceType, String sourceName, byte[] bytes) {
        List<HistorySpreadsheetParser.RowData> rows = parser.parse(bytes);
        int imported = 0, skipped = 0, errors = 0;
        long maxSpecNumber = 0;
        List<String> messages = new ArrayList<>();

        TransactionTemplate rowTransaction = new TransactionTemplate(transactionManager);
        rowTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        for (HistorySpreadsheetParser.RowData row : rows) {
            if ("UNKNOWN".equals(row.documentType()) || row.number() == null || row.number().isBlank() || row.documentDate() == null) {
                errors++;
                messages.add("Строка " + row.rowNumber() + ": не хватает типа, номера или исторической даты");
                continue;
            }
            try {
                ImportOutcome outcome = rowTransaction.execute(status -> importOne(row));
                if (outcome == null) {
                    errors++;
                    messages.add("Строка " + row.rowNumber() + ": импорт не завершён");
                } else if (outcome.skipped()) {
                    skipped++;
                } else {
                    imported++;
                    if (outcome.specificationNumber() != null) {
                        maxSpecNumber = Math.max(maxSpecNumber, outcome.specificationNumber());
                    }
                }
            } catch (RuntimeException ex) {
                errors++;
                messages.add("Строка " + row.rowNumber() + ": " + safeMessage(ex));
            }
        }

        if (maxSpecNumber > 0) sequenceService.ensureNextSpecificationAtLeast(maxSpecNumber + 1);

        ImportBatch batch = new ImportBatch();
        batch.setSourceType(sourceType);
        batch.setSourceName(sourceName);
        batch.setCreatedBy(currentUserService.requireCurrentUser());
        batch.setImportedRows(imported);
        batch.setSkippedRows(skipped);
        batch.setErrorRows(errors);
        try {
            batch.setDetailsJson(objectMapper.writeValueAsString(messages));
        } catch (Exception ignored) {
            batch.setDetailsJson(String.join("\n", messages));
        }
        batch = importBatchRepository.save(batch);
        return new HistoryImportDtos.CommitResponse(batch.getId(), imported, skipped, errors, messages.stream().limit(100).toList());
    }

    private ImportOutcome importOne(HistorySpreadsheetParser.RowData row) {
        Company company = resolveCompany(row);
        if (isDuplicate(row, company)) return new ImportOutcome(true, null);

        StoredPaths paths = fetchLinkedDocument(row.linkedFileUrl(), row.documentType(), row.number());
        if ("CONTRACT".equals(row.documentType())) {
            Contract contract = new Contract();
            contract.setCompany(company);
            contract.setNumber(row.number());
            contract.setContractDate(row.documentDate());
            contract.setAutoRenewal(true);
            contract.setActive(true);
            contract.setDocumentStatus(DocumentStatus.ARCHIVED);
            contract.setDocxPath(paths.docxPath());
            contract.setPdfPath(paths.pdfPath());
            contract = contractRepository.saveAndFlush(contract);
            versionService.capture("CONTRACT", contract.getId(), contract.getDocxPath(), contract.getPdfPath(), "Импорт исторического договора");
            return new ImportOutcome(false, null);
        }

        long number;
        try {
            String digits = row.number().replaceAll("[^0-9]", "");
            if (digits.isBlank()) throw new NumberFormatException();
            number = Long.parseLong(digits);
        } catch (NumberFormatException ex) {
            throw new BusinessException("номер спецификации должен содержать цифры");
        }

        Specification specification = new Specification();
        specification.setNumber(number);
        specification.setSpecificationDate(row.documentDate());
        specification.setCompany(company);
        specification.setDeal(null);
        specification.setTotalBeforeDiscount(nz(row.amount()));
        specification.setTotalAfterDiscount(nz(row.amount()));
        specification.setVatAmount(BigDecimal.ZERO);
        specification.setStatus(DocumentStatus.ARCHIVED);
        specification.setDocxPath(paths.docxPath());
        specification.setPdfPath(paths.pdfPath());
        if (row.contractNumber() != null && !row.contractNumber().isBlank()) {
            contractRepository.findByCompany_IdAndNumberIgnoreCase(company.getId(), row.contractNumber()).ifPresent(specification::setContract);
        }
        specification = specificationRepository.saveAndFlush(specification);
        versionService.capture("SPECIFICATION", specification.getId(), specification.getDocxPath(), specification.getPdfPath(), "Импорт исторической спецификации");
        return new ImportOutcome(false, number);
    }

    private Company resolveCompany(HistorySpreadsheetParser.RowData row) {
        Optional<Company> existing = findCompany(row);
        if (existing.isPresent()) return existing.get();
        Company c = new Company();
        c.setLegalForm(LegalForm.valueOf(row.legalForm())); c.setShortName(row.shortName()); c.setCanonicalName(row.canonicalName());
        c.setInn(blankToNull(row.unp())); c.setStatus(ClientStatus.RETURN); c.setCreatedBy(currentUserService.requireCurrentUser());
        return companyRepository.save(c);
    }

    private Optional<Company> findCompany(HistorySpreadsheetParser.RowData row) {
        if (row.unp() != null && !row.unp().isBlank()) {
            Optional<Company> byInn = companyRepository.findByInn(row.unp());
            if (byInn.isPresent()) return byInn;
        }
        return companyRepository.findByCanonicalNameIgnoreCase(row.canonicalName());
    }

    private boolean isDuplicate(HistorySpreadsheetParser.RowData row, Company company) {
        if ("CONTRACT".equals(row.documentType())) return contractRepository.existsByCompany_IdAndNumberIgnoreCase(company.getId(), row.number());
        if ("SPECIFICATION".equals(row.documentType())) {
            try { return specificationRepository.findByNumber(Long.parseLong(row.number().replaceAll("[^0-9]", ""))).isPresent(); }
            catch (NumberFormatException ex) { return false; }
        }
        return false;
    }

    private StoredPaths fetchLinkedDocument(String url, String type, String number) {
        if (url == null || url.isBlank()) return new StoredPaths(null, null);
        if (!driveStorage.isEnabled()) throw new BusinessException("В строке есть Google Drive ссылка, но интеграция Drive отключена");
        DriveStorageService.DownloadResult file = driveStorage.download(extractDriveId(url));
        String fileName = file.fileName() == null ? "document" : file.fileName();
        String extension = extension(fileName, file.mimeType());
        String safeBase = "import-" + type.toLowerCase(Locale.ROOT) + "-" + number.replaceAll("[^A-Za-zА-Яа-я0-9_-]", "-") + "-" + UUID.randomUUID();
        if (!extension.equalsIgnoreCase(".docx") && !extension.equalsIgnoreCase(".pdf")) {
            throw new BusinessException("Связанный файл должен быть DOCX/PDF или Google Docs. Получен: " + fileName);
        }
        String path = storage.save(safeBase + extension, file.bytes());
        return extension.equalsIgnoreCase(".docx") ? new StoredPaths(path, null) : new StoredPaths(null, path);
    }

    private DriveStorageService.DownloadResult downloadDrive(String urlOrFileId) {
        if (!driveStorage.isEnabled()) throw new BusinessException("Google Drive интеграция отключена");
        return driveStorage.download(extractDriveId(urlOrFileId));
    }

    private String extractDriveId(String value) {
        String v = value == null ? "" : value.trim();
        if (v.matches("[A-Za-z0-9_-]{15,}")) return v;
        Matcher m = DRIVE_ID.matcher(v);
        if (m.find()) return m.group(1);
        throw new BusinessException("Не удалось определить fileId из ссылки Google Drive/Sheets");
    }

    private String extension(String name, String mime) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".docx")) return ".docx";
        if (lower.endsWith(".pdf")) return ".pdf";
        if (lower.endsWith(".xlsx")) return ".xlsx";
        if (lower.endsWith(".xls")) return ".xls";
        if (mime != null && mime.contains("wordprocessingml")) return ".docx";
        if (mime != null && mime.equals("application/pdf")) return ".pdf";
        return ".bin";
    }

    private BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private String safeMessage(Throwable ex) { String m=ex.getMessage(); return m==null?ex.getClass().getSimpleName():(m.length()>300?m.substring(0,300):m); }
    private record StoredPaths(String docxPath, String pdfPath) {}
    private record ImportOutcome(boolean skipped, Long specificationNumber) {}
}
