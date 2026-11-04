package com.adrotation.crm.documents.service;

import com.adrotation.crm.audit.service.AuditService;
import com.adrotation.crm.company.entity.Company;
import com.adrotation.crm.company.enums.ClientStatus;
import com.adrotation.crm.company.service.CompanyService;
import com.adrotation.crm.crm.entity.Deal;
import com.adrotation.crm.crm.entity.DealPlacement;
import com.adrotation.crm.crm.enums.CalledToday;
import com.adrotation.crm.crm.enums.DocumentIntent;
import com.adrotation.crm.crm.repository.DealPlacementRepository;
import com.adrotation.crm.crm.repository.DealRepository;
import com.adrotation.crm.documents.dto.SpecificationDtos;
import com.adrotation.crm.documents.entity.Contract;
import com.adrotation.crm.documents.entity.OrganizationProfile;
import com.adrotation.crm.documents.entity.Specification;
import com.adrotation.crm.documents.entity.SpecificationItem;
import com.adrotation.crm.documents.enums.DocumentPackageType;
import com.adrotation.crm.documents.enums.DocumentStatus;
import com.adrotation.crm.documents.repository.SpecificationRepository;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SpecificationService {

    private final DealRepository dealRepository;
    private final CompanyService companyService;
    private final DealPlacementRepository placementRepository;
    private final com.adrotation.crm.crm.service.PlacementPolicy placementPolicy;
    private final SpecificationRepository specificationRepository;
    private final OrganizationProfileService organizationProfileService;
    private final ContractService contractService;
    private final DocumentSequenceService sequenceService;
    private final SpecificationTemplateService templateService;
    private final GotenbergDocumentConverter pdfConverter;
    private final LocalDocumentStorageService storage;
    private final AuditService auditService;
    private final DocumentVersionService versionService;

    @Value("${app.time-zone:Europe/Minsk}")
    private String timeZone;

    @Transactional(readOnly = true)
    public SpecificationDtos.DocumentOfferResponse offer(Long dealId) {
        Deal deal = requireDeal(dealId);
        List<String> missing = missingFields(deal);
        Contract activeContract = contractService.activeForCompany(deal.getCompany().getId());

        boolean communicationEligible = deal.getCalledToday() == CalledToday.YES
                && deal.isGoalAchieved()
                && isDocumentIntentSelected(deal.getDocumentIntent());

        DocumentPackageType packageType = packageFor(
                deal.getCompany().getStatus(),
                deal.getDocumentIntent()
        );

        if (deal.getCompany().getStatus() != ClientStatus.NEW && activeContract == null) {
            missing.add("activeContract");
        }

        boolean eligible = communicationEligible && missing.isEmpty();
        String reason = eligible ? null : eligibilityReason(deal, missing);

        return new SpecificationDtos.DocumentOfferResponse(
                eligible,
                reason,
                packageType,
                deal.getCompany().getStatus(),
                activeContract != null,
                activeContract == null ? null : activeContract.getId(),
                activeContract == null ? null : activeContract.getNumber(),
                List.copyOf(missing)
        );
    }

    @Transactional
    public SpecificationDtos.GenerateDocumentsResponse generate(Long dealId) {
        Deal deal = requireDeal(dealId);
        ensureCanGenerate(deal);

        if (deal.getCompany().getStatus() == ClientStatus.NEW) {
            throw new BusinessException(
                    "Для нового клиента требуется шаблон стандартного договора и правило его нумерации. "
                            + "Добавьте шаблон договора перед генерацией полного комплекта документов."
            );
        }

        Contract contract = contractService.activeForCompany(deal.getCompany().getId());
        if (contract == null) {
            throw new BusinessException("У постоянного клиента нет действующего основного договора");
        }

        List<DealPlacement> placements = placementRepository
                .findByDeal_IdOrderByPeriodStartAscIdAsc(dealId);

        if (placements.isEmpty()) {
            throw new BusinessException("Добавьте в сделку хотя бы одну строку размещения");
        }

        OrganizationProfile executor = organizationProfileService.require();
        Specification specification = buildSpecification(deal, contract, placements);
        specification = specificationRepository.save(specification);

        String baseName = "specification-" + specification.getNumber() + "-deal-" + deal.getId();
        String docxPath = null;
        String pdfPath = null;

        try {
            byte[] docx = templateService.render(executor, specification);
            docxPath = storage.save(baseName + ".docx", docx);
            specification.setDocxPath(docxPath);

            String pdfError = null;
            try {
                byte[] pdf = pdfConverter.docxToPdf(baseName + ".docx", docx);
                if (pdf != null && pdf.length > 0) {
                    pdfPath = storage.save(baseName + ".pdf", pdf);
                    specification.setPdfPath(pdfPath);
                }
            } catch (RuntimeException e) {
                pdfError = e.getMessage();
                specification.setStatus(DocumentStatus.PARTIALLY_GENERATED);
            }

            specification = specificationRepository.save(specification);
            versionService.capture("SPECIFICATION", specification.getId(), specification.getDocxPath(), specification.getPdfPath(), "Генерация из CRM");
            auditService.record(
                    "SPECIFICATION",
                    specification.getId(),
                    "generated",
                    null,
                    "№" + specification.getNumber()
            );

            String message = pdfPath != null
                    ? "Спецификация DOCX/PDF сформирована"
                    : "DOCX сформирован. PDF не создан: " + safeMessage(pdfError);

            return new SpecificationDtos.GenerateDocumentsResponse(
                    packageFor(deal.getCompany().getStatus(), deal.getDocumentIntent()),
                    false,
                    message,
                    toDto(specification)
            );
        } catch (RuntimeException e) {
            storage.delete(docxPath);
            storage.delete(pdfPath);
            throw e;
        }
    }

    @Transactional
    public SpecificationDtos.SpecificationResponse createManual(SpecificationDtos.ManualSpecificationRequest request) {
        Company company = companyService.require(request.companyId());
        Contract contract = contractService.require(request.contractId());
        if (!contract.getCompany().getId().equals(company.getId())) {
            throw new BusinessException("Выбранный договор относится к другой компании");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException("Добавьте хотя бы одну строку размещения");
        }

        Specification specification = new Specification();
        specification.setNumber(sequenceService.nextSpecificationNumber());
        specification.setSpecificationDate(request.specificationDate() == null
                ? LocalDate.now(ZoneId.of(timeZone))
                : request.specificationDate());
        specification.setDeal(null);
        specification.setCompany(company);
        specification.setContract(contract);

        BigDecimal totalBeforeDiscount = BigDecimal.ZERO;
        BigDecimal totalAfterDiscount = BigDecimal.ZERO;
        BigDecimal vatTotal = BigDecimal.ZERO;
        int lineNumber = 1;

        for (SpecificationDtos.ManualSpecificationItemRequest itemRequest : request.items()) {
            placementPolicy.validate(itemRequest.mediaType(), itemRequest.periodStart(), itemRequest.periodEnd());
            boolean vatExempt = itemRequest.vatExempt() == null || itemRequest.vatExempt();
            BigDecimal vatRate = itemRequest.vatRate() == null ? BigDecimal.ZERO : itemRequest.vatRate();
            if (vatExempt && vatRate.signum() > 0) {
                throw new BusinessException("Для строки без НДС ставка НДС должна быть 0");
            }

            SpecificationItem item = snapshotManual(specification, lineNumber++, itemRequest);
            specification.getItems().add(item);
            totalBeforeDiscount = totalBeforeDiscount.add(item.getTotalBeforeDiscount());
            totalAfterDiscount = totalAfterDiscount.add(item.getTotalAfterDiscount());
            vatTotal = vatTotal.add(item.getVatAmount());
        }

        specification.setTotalBeforeDiscount(money(totalBeforeDiscount));
        specification.setTotalAfterDiscount(money(totalAfterDiscount));
        specification.setVatAmount(money(vatTotal));
        specification = specificationRepository.save(specification);

        OrganizationProfile executor = organizationProfileService.require();
        String baseName = "specification-" + specification.getNumber() + "-manual";
        String docxPath = null;
        String pdfPath = null;

        try {
            byte[] docx = templateService.render(executor, specification);
            docxPath = storage.save(baseName + ".docx", docx);
            specification.setDocxPath(docxPath);

            try {
                byte[] pdf = pdfConverter.docxToPdf(baseName + ".docx", docx);
                if (pdf != null && pdf.length > 0) {
                    pdfPath = storage.save(baseName + ".pdf", pdf);
                    specification.setPdfPath(pdfPath);
                }
            } catch (RuntimeException e) {
                specification.setStatus(DocumentStatus.PARTIALLY_GENERATED);
            }

            specification = specificationRepository.save(specification);
            versionService.capture("SPECIFICATION", specification.getId(), specification.getDocxPath(), specification.getPdfPath(), "Ручная генерация");
            auditService.record("SPECIFICATION", specification.getId(), "manualGenerated", null, "№" + specification.getNumber());
            return toDto(specification);
        } catch (RuntimeException e) {
            storage.delete(docxPath);
            storage.delete(pdfPath);
            throw e;
        }
    }

    @Transactional
    public SpecificationDtos.SpecificationResponse cloneSpecification(Long sourceId, SpecificationDtos.CloneSpecificationRequest request) {
        Specification source = require(sourceId);
        if (source.getContract() == null) {
            throw new BusinessException("Для создания новой спецификации на основе старой нужен связанный договор");
        }
        if (source.getItems().isEmpty()) {
            throw new BusinessException("В исходной спецификации нет строк размещения для копирования");
        }
        List<SpecificationDtos.ManualSpecificationItemRequest> items = source.getItems().stream()
                .map(item -> new SpecificationDtos.ManualSpecificationItemRequest(
                        item.getCityName(), item.getStandFormat(), item.getPeriodStart(), item.getPeriodEnd(),
                        item.getQuantity(), item.getUnitPrice(), item.getDiscountPercent(), item.getPromotionName(),
                        item.isVatExempt(), item.getVatRate(), item.getMediaType()))
                .toList();
        SpecificationDtos.ManualSpecificationRequest cloneRequest = new SpecificationDtos.ManualSpecificationRequest(
                source.getCompany().getId(), source.getContract().getId(),
                request == null ? null : request.specificationDate(), items);
        SpecificationDtos.SpecificationResponse result = createManual(cloneRequest);
        auditService.record("SPECIFICATION", result.id(), "clonedFrom", null, source.getNumber());
        return result;
    }

    @Transactional(readOnly = true)
    public List<SpecificationDtos.SpecificationResponse> listForDeal(Long dealId) {
        requireDeal(dealId);
        return specificationRepository.findByDeal_IdOrderByCreatedAtDesc(dealId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Specification require(Long id) {
        return specificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Спецификация не найдена: " + id));
    }

    public SpecificationDtos.SpecificationResponse toDto(Specification specification) {
        List<SpecificationDtos.ItemResponse> items = specification.getItems()
                .stream()
                .map(item -> new SpecificationDtos.ItemResponse(
                        item.getLineNumber(),
                        item.getCityName(),
                        item.getStandFormat(),
                        item.getPeriodStart(),
                        item.getPeriodEnd(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getDiscountPercent(),
                        item.getPromotionName(),
                        item.isVatExempt(),
                        item.getVatRate(),
                        item.getVatAmount(),
                        item.getTotalBeforeDiscount(),
                        item.getTotalAfterDiscount(),
                        item.getMediaType()
                ))
                .toList();

        return new SpecificationDtos.SpecificationResponse(
                specification.getId(),
                specification.getNumber(),
                specification.getSpecificationDate(),
                specification.getDeal() == null ? null : specification.getDeal().getId(),
                specification.getCompany().getId(),
                specification.getContract() == null ? null : specification.getContract().getId(),
                specification.getContract() == null ? null : specification.getContract().getNumber(),
                specification.getTotalBeforeDiscount(),
                specification.getTotalAfterDiscount(),
                specification.getVatAmount(),
                specification.getStatus(),
                specification.getDocxPath() == null
                        ? null
                        : "/api/specifications/" + specification.getId() + "/download/docx",
                specification.getPdfPath() == null
                        ? null
                        : "/api/specifications/" + specification.getId() + "/download/pdf",
                items,
                specification.getCreatedAt()
        );
    }

    private void ensureCanGenerate(Deal deal) {
        List<String> missing = missingFields(deal);

        if (deal.getCompany().getStatus() != ClientStatus.NEW
                && contractService.activeForCompany(deal.getCompany().getId()) == null) {
            missing.add("activeContract");
        }

        String reason = eligibilityReason(deal, missing);
        if (reason != null) {
            throw new BusinessException("Документы пока нельзя сформировать: " + reason);
        }
    }

    private Specification buildSpecification(
            Deal deal,
            Contract contract,
            List<DealPlacement> placements
    ) {
        Specification specification = new Specification();
        specification.setNumber(sequenceService.nextSpecificationNumber());
        specification.setSpecificationDate(LocalDate.now(ZoneId.of(timeZone)));
        specification.setDeal(deal);
        specification.setCompany(deal.getCompany());
        specification.setContract(contract);

        BigDecimal totalBeforeDiscount = BigDecimal.ZERO;
        BigDecimal totalAfterDiscount = BigDecimal.ZERO;
        BigDecimal vatTotal = BigDecimal.ZERO;
        int lineNumber = 1;

        for (DealPlacement placement : placements) {
            SpecificationItem item = snapshot(specification, lineNumber++, placement);
            specification.getItems().add(item);
            totalBeforeDiscount = totalBeforeDiscount.add(item.getTotalBeforeDiscount());
            totalAfterDiscount = totalAfterDiscount.add(item.getTotalAfterDiscount());
            vatTotal = vatTotal.add(item.getVatAmount());
        }

        specification.setTotalBeforeDiscount(money(totalBeforeDiscount));
        specification.setTotalAfterDiscount(money(totalAfterDiscount));
        specification.setVatAmount(money(vatTotal));
        return specification;
    }

    private SpecificationItem snapshot(
            Specification specification,
            int lineNumber,
            DealPlacement placement
    ) {
        BigDecimal net = placement.getUnitPrice()
                .multiply(BigDecimal.valueOf(placement.getQuantity()));

        BigDecimal discountPercent = placement.getDiscountPercent() == null
                ? BigDecimal.ZERO
                : placement.getDiscountPercent();

        BigDecimal discountMultiplier = BigDecimal.ONE.subtract(
                discountPercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
        );

        BigDecimal discountedNet = net.multiply(discountMultiplier);
        BigDecimal vat = placement.isVatExempt()
                ? BigDecimal.ZERO
                : discountedNet
                        .multiply(placement.getVatRate())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal total = discountedNet.add(vat);

        SpecificationItem item = new SpecificationItem();
        item.setSpecification(specification);
        item.setLineNumber(lineNumber);
        item.setCityName(placement.getCity().getName());
        item.setStandFormat(placement.getStandFormat().name());
        item.setMediaType(placement.getMediaType());
        item.setPeriodStart(placement.getPeriodStart());
        item.setPeriodEnd(placement.getPeriodEnd());
        item.setQuantity(placement.getQuantity());
        item.setUnitPrice(money(placement.getUnitPrice()));
        item.setDiscountPercent(money(discountPercent));
        item.setPromotionName(placement.getPromotionName());
        item.setVatExempt(placement.isVatExempt());
        item.setVatRate(money(placement.getVatRate()));
        item.setVatAmount(money(vat));
        item.setTotalBeforeDiscount(money(net));
        item.setTotalAfterDiscount(money(total));
        return item;
    }

    private SpecificationItem snapshotManual(
            Specification specification,
            int lineNumber,
            SpecificationDtos.ManualSpecificationItemRequest request
    ) {
        BigDecimal discountPercent = request.discountPercent() == null ? BigDecimal.ZERO : request.discountPercent();
        BigDecimal net = request.unitPrice().multiply(BigDecimal.valueOf(request.quantity()));
        BigDecimal discountMultiplier = BigDecimal.ONE.subtract(
                discountPercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
        );
        BigDecimal discountedNet = net.multiply(discountMultiplier);
        boolean vatExempt = request.vatExempt() == null || request.vatExempt();
        BigDecimal vatRate = request.vatRate() == null ? BigDecimal.ZERO : request.vatRate();
        BigDecimal vat = vatExempt
                ? BigDecimal.ZERO
                : discountedNet.multiply(vatRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = discountedNet.add(vat);

        SpecificationItem item = new SpecificationItem();
        item.setSpecification(specification);
        item.setLineNumber(lineNumber);
        item.setCityName(request.cityName().trim());
        item.setStandFormat(request.standFormat().trim());
        item.setMediaType(com.adrotation.crm.crm.enums.MediaType.orDefault(request.mediaType()));
        item.setPeriodStart(request.periodStart());
        item.setPeriodEnd(request.periodEnd());
        item.setQuantity(request.quantity());
        item.setUnitPrice(money(request.unitPrice()));
        item.setDiscountPercent(money(discountPercent));
        item.setPromotionName(request.promotionName());
        item.setVatExempt(vatExempt);
        item.setVatRate(money(vatRate));
        item.setVatAmount(money(vat));
        item.setTotalBeforeDiscount(money(net));
        item.setTotalAfterDiscount(money(total));
        return item;
    }

    private List<String> missingFields(Deal deal) {
        List<String> missing = new ArrayList<>();
        Company company = deal.getCompany();

        if (blank(company.getInn())) {
            missing.add("company.unp");
        }
        if (blank(company.getLegalAddress())) {
            missing.add("company.legalAddress");
        }
        if (blank(company.getPostalAddress())) {
            missing.add("company.postalAddress");
        }
        if (blank(company.getSettlementAccount())) {
            missing.add("company.settlementAccount");
        }
        if (blank(company.getBankName())) {
            missing.add("company.bankName");
        }
        if (blank(company.getBankBic())) {
            missing.add("company.bankBic");
        }
        if (placementRepository.findByDeal_IdOrderByPeriodStartAscIdAsc(deal.getId()).isEmpty()) {
            missing.add("deal.placements");
        }

        return missing;
    }

    private String eligibilityReason(Deal deal, List<String> missing) {
        if (deal.getCalledToday() != CalledToday.YES) {
            return "Статус звонка должен быть «Дозвонился = Да»";
        }
        if (!deal.isGoalAchieved()) {
            return "Поле «Цель достигнута» должно быть Да";
        }
        if (!isDocumentIntentSelected(deal.getDocumentIntent())) {
            return "Выберите результат сделки: Договор или Счёт";
        }
        if (!missing.isEmpty()) {
            return "Не заполнены обязательные данные: " + String.join(", ", missing);
        }
        return null;
    }

    private DocumentPackageType packageFor(ClientStatus status, DocumentIntent intent) {
        if (!isDocumentIntentSelected(intent)) {
            return DocumentPackageType.NONE;
        }
        if (status == ClientStatus.NEW) {
            return DocumentPackageType.CONTRACT_AND_SPECIFICATION;
        }
        return intent == DocumentIntent.INVOICE
                ? DocumentPackageType.INVOICE_OR_SPECIFICATION
                : DocumentPackageType.SPECIFICATION_ONLY;
    }

    private boolean isDocumentIntentSelected(DocumentIntent intent) {
        return intent == DocumentIntent.CONTRACT || intent == DocumentIntent.INVOICE;
    }

    private Deal requireDeal(Long id) {
        return dealRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CRM-карточка не найдена: " + id));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String safeMessage(String message) {
        return message == null || message.isBlank() ? "неизвестная ошибка конвертации" : message;
    }
}
