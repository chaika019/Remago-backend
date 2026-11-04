package com.adrotation.crm.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

public final class SecurityDtos {
    private SecurityDtos() {}

    public record SessionView(String id, String ipAddress, String userAgent, Instant createdAt, Instant lastSeenAt,
                              Instant expiresAt, boolean current, boolean revoked) {}
    public record SessionsResponse(List<SessionView> sessions) {}

    public record TwoFactorRequest(@NotBlank String challengeId,
                                   @NotBlank @Pattern(regexp = "\\d{6}", message = "Код должен состоять из 6 цифр") String code) {}

    public record AuthAttemptView(Long id, Long userId, String email, String eventType, boolean success,
                                  String ipAddress, String userAgent, String failureReason, Instant createdAt) {}

    public record DiagnosticCheck(String name, String status, String details) {}
    public record DiagnosticsResponse(Instant checkedAt, String application, String version, List<DiagnosticCheck> checks) {}
}
