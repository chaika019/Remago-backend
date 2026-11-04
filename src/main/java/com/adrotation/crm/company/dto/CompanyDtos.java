package com.adrotation.crm.company.dto;

import com.adrotation.crm.company.enums.ClientStatus;
import com.adrotation.crm.company.enums.DeliveryChannel;
import com.adrotation.crm.company.enums.LegalForm;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class CompanyDtos {
    private CompanyDtos() {}

    public record CompanyCreateRequest(
            @NotNull LegalForm legalForm,
            @NotBlank String name,
            String inn,
            ClientStatus status,
            String legalAddress,
            String postalAddress,
            String settlementAccount,
            String bankName,
            String bankBic
    ) {}

    public record CompanyUpdateRequest(
            LegalForm legalForm,
            String name,
            String inn,
            Boolean clearInn,
            ClientStatus status,
            String legalAddress,
            String postalAddress,
            String settlementAccount,
            String bankName,
            String bankBic
    ) {}

    public record CompanyStatusRequest(@NotNull ClientStatus status) {}

    public record CompanyResponse(
            Long id,
            LegalForm legalForm,
            String shortName,
            String canonicalName,
            String inn,
            ClientStatus status,
            String legalAddress,
            String postalAddress,
            String settlementAccount,
            String bankName,
            String bankBic,
            Long createdById,
            Instant createdAt
    ) {}

    public record ContactRequest(
            @NotBlank String fio,
            String position,
            String phone,
            @Email String email,
            String telegramChatId,
            String viberUserId,
            DeliveryChannel preferredDeliveryChannel
    ) {}

    public record ContactResponse(
            Long id,
            Long companyId,
            String fio,
            String position,
            String phone,
            String email,
            String telegramChatId,
            String viberUserId,
            DeliveryChannel preferredDeliveryChannel
    ) {}
}
