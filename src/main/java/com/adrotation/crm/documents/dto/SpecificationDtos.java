package com.adrotation.crm.documents.dto;

import com.adrotation.crm.company.enums.ClientStatus;
import com.adrotation.crm.company.enums.DeliveryChannel;
import com.adrotation.crm.documents.enums.DocumentPackageType;
import com.adrotation.crm.documents.enums.DocumentStatus;
import jakarta.validation.Valid;
import com.adrotation.crm.crm.enums.MediaType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class SpecificationDtos {
    private SpecificationDtos() {}

    public record DocumentOfferResponse(
            boolean eligible,
            String reason,
            DocumentPackageType recommendedPackage,
            ClientStatus clientStatus,
            boolean hasActiveContract,
            Long activeContractId,
            String activeContractNumber,
            List<String> missingFields
    ) {}

    public record ContractRequest(
            @NotNull Long companyId,
            @NotBlank String number,
            @NotNull LocalDate contractDate,
            LocalDate validUntil,
            Boolean autoRenewal,
            Boolean active
    ) {}

    public record ContractResponse(
            Long id,
            Long companyId,
            String number,
            LocalDate contractDate,
            LocalDate validUntil,
            boolean autoRenewal,
            boolean active,
            DocumentStatus status,
            String docxDownloadUrl,
            String pdfDownloadUrl
    ) {}

    public record ManualSpecificationItemRequest(
            @NotBlank String cityName,
            @NotBlank String standFormat,
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd,
            @NotNull @Positive Integer quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal discountPercent,
            String promotionName,
            Boolean vatExempt,
            @DecimalMin("0.00") BigDecimal vatRate,
            MediaType mediaType
    ) {}

    public record ManualSpecificationRequest(
            @NotNull Long companyId,
            @NotNull Long contractId,
            LocalDate specificationDate,
            @NotEmpty List<@Valid ManualSpecificationItemRequest> items
    ) {}

    public record CloneSpecificationRequest(
            LocalDate specificationDate
    ) {}

    public record ItemResponse(
            Integer lineNumber,
            String cityName,
            String standFormat,
            LocalDate periodStart,
            LocalDate periodEnd,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal discountPercent,
            String promotionName,
            boolean vatExempt,
            BigDecimal vatRate,
            BigDecimal vatAmount,
            BigDecimal totalBeforeDiscount,
            BigDecimal totalAfterDiscount,
            MediaType mediaType
    ) {}

    public record SpecificationResponse(
            Long id,
            Long number,
            LocalDate specificationDate,
            Long dealId,
            Long companyId,
            Long contractId,
            String contractNumber,
            BigDecimal totalBeforeDiscount,
            BigDecimal totalAfterDiscount,
            BigDecimal vatAmount,
            DocumentStatus status,
            String docxDownloadUrl,
            String pdfDownloadUrl,
            List<ItemResponse> items,
            Instant createdAt
    ) {}

    public record GenerateDocumentsResponse(
            DocumentPackageType packageType,
            boolean contractFileGenerated,
            String contractMessage,
            SpecificationResponse specification
    ) {}

    public record DeliveryRequest(
            @NotNull DeliveryChannel channel,
            Long contactId,
            String recipient
    ) {}

    public record DeliveryResponse(
            Long id,
            DeliveryChannel channel,
            String recipient,
            String status,
            Instant sentAt,
            String errorMessage
    ) {}
}
