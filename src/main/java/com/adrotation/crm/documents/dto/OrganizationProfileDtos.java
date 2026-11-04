package com.adrotation.crm.documents.dto;

public final class OrganizationProfileDtos {
    private OrganizationProfileDtos() {}

    public record UpdateRequest(
            String legalName,
            String unp,
            String settlementAccount,
            String bankName,
            String bankBic,
            String bankCity,
            String legalAddress,
            String postalAddress,
            String registrationInfo,
            String signatoryName
    ) {}

    public record Response(
            Long id,
            String legalName,
            String unp,
            String settlementAccount,
            String bankName,
            String bankBic,
            String bankCity,
            String legalAddress,
            String postalAddress,
            String registrationInfo,
            String signatoryName
    ) {}
}
