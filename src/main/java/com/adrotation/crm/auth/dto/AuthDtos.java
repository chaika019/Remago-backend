package com.adrotation.crm.auth.dto;

import com.adrotation.crm.user.enums.Permission;
import com.adrotation.crm.user.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 180) String fullName,
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 128) String password
    ) {}

    public record VerifyEmailRequest(
            @Email @NotBlank String email,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "Код должен состоять из 6 цифр") String code
    ) {}

    public record ResendVerificationRequest(@Email @NotBlank String email) {}

    public record TwoFactorVerifyRequest(
            @NotBlank String challengeId,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "Код должен состоять из 6 цифр") String code
    ) {}

    public record RegisterResponse(String email, boolean verificationRequired, long codeExpiresInSeconds,
                                   long resendAfterSeconds, String message) {}

    public record VerificationActionResponse(String email, long resendAfterSeconds, String message) {}

    public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, UserView user) {}

    public record LoginResponse(
            AuthResponse auth,
            boolean secondFactorRequired,
            String challengeId,
            String maskedEmail,
            long codeExpiresInSeconds
    ) {}

    public record UserView(Long id, String fullName, String email, Set<Role> roles, Set<Permission> permissions) {
        public boolean pending() { return roles != null && roles.contains(Role.PENDING); }
    }
}
