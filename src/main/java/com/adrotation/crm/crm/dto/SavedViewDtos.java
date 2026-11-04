package com.adrotation.crm.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class SavedViewDtos {
    private SavedViewDtos() {}

    public record SaveRequest(
            @NotBlank @Size(max = 180) String name,
            @NotBlank @Size(max = 50) String module,
            @NotBlank String filtersJson,
            boolean shared
    ) {}

    public record Response(
            Long id,
            String name,
            String module,
            String filtersJson,
            boolean shared,
            Long ownerId,
            String ownerName,
            Instant createdAt
    ) {}
}
