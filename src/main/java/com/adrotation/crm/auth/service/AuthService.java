package com.adrotation.crm.auth.service;

import com.adrotation.crm.auth.dto.AuthDtos;
import com.adrotation.crm.auth.security.CurrentUserService;
import com.adrotation.crm.auth.security.JwtService;
import com.adrotation.crm.security.entity.TwoFactorChallenge;
import com.adrotation.crm.security.service.AuthAttemptService;
import com.adrotation.crm.security.service.TwoFactorService;
import com.adrotation.crm.security.service.UserSessionService;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.shared.exception.EmailVerificationRequiredException;
import com.adrotation.crm.shared.exception.UnauthorizedException;
import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.user.enums.Role;
import com.adrotation.crm.user.repository.AppUserRepository;
import com.adrotation.crm.user.service.UserAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserAccessPolicy accessPolicy;
    private final EmailVerificationService emailVerificationService;
    private final TwoFactorService twoFactorService;
    private final UserSessionService sessionService;
    private final AuthAttemptService authAttemptService;
    private final CurrentUserService currentUserService;

    @Value("${app.security.super-admin-emails:egorchuiko7@gmail.com}")
    private String superAdminEmails;

    @Transactional
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request, String ip, String userAgent) {
        String email = normalizeEmail(request.email());
        AppUser user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (user == null || !user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            authAttemptService.record(user, email, "LOGIN", false, ip, userAgent, "Неверный email или пароль");
            throw new UnauthorizedException("Неверный email или пароль");
        }

        if (!user.isEmailVerified()) {
            authAttemptService.record(user, email, "LOGIN", false, ip, userAgent, "Email не подтверждён");
            throw new EmailVerificationRequiredException(user.getEmail());
        }

        if (user.getRoles().contains(Role.SUPER_ADMIN) && twoFactorService.enabled()) {
            TwoFactorChallenge challenge = twoFactorService.issue(user, ip, userAgent);
            authAttemptService.record(user, email, "LOGIN_PASSWORD", true, ip, userAgent, null);
            return new AuthDtos.LoginResponse(null, true, challenge.getId(), twoFactorService.mask(user.getEmail()), twoFactorService.expiresInSeconds());
        }

        AuthDtos.AuthResponse auth = issueNewSession(user, ip, userAgent);
        authAttemptService.record(user, email, "LOGIN", true, ip, userAgent, null);
        return new AuthDtos.LoginResponse(auth, false, null, null, 0);
    }

    @Transactional
    public AuthDtos.AuthResponse verifySecondFactor(AuthDtos.TwoFactorVerifyRequest request, String ip, String userAgent) {
        AppUser user;
        try {
            user = twoFactorService.verify(request.challengeId(), request.code());
        } catch (BusinessException ex) {
            authAttemptService.record(null, null, "TWO_FACTOR", false, ip, userAgent, ex.getMessage());
            throw ex;
        }
        AuthDtos.AuthResponse auth = issueNewSession(user, ip, userAgent);
        authAttemptService.record(user, user.getEmail(), "TWO_FACTOR", true, ip, userAgent, null);
        return auth;
    }

    @Transactional
    public AuthDtos.RegisterResponse register(AuthDtos.RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (isReservedSuperAdminEmail(email)) {
            throw new BusinessException("Этот email зарезервирован для системной учётной записи. Используйте существующий вход.");
        }
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new BusinessException("Пользователь с таким email уже зарегистрирован");
        }

        AppUser user = new AppUser();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        user.setEmailVerified(false);
        Set<Role> roles = EnumSet.of(Role.PENDING);
        user.setRoles(roles);
        user.setPermissions(accessPolicy.permissionsFor(roles));
        user = userRepository.saveAndFlush(user);
        emailVerificationService.sendInitialCode(user);
        userRepository.save(user);

        return new AuthDtos.RegisterResponse(user.getEmail(), true, emailVerificationService.codeExpiresInSeconds(),
                emailVerificationService.resendAfterSeconds(), "Код подтверждения отправлен на email");
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthDtos.AuthResponse verifyEmail(AuthDtos.VerifyEmailRequest request, String ip, String userAgent) {
        String email = normalizeEmail(request.email());
        AppUser user = userRepository.findByEmailIgnoreCaseForUpdate(email)
                .orElseThrow(() -> new BusinessException("Пользователь не найден"));
        if (!user.isEnabled()) throw new BusinessException("Учётная запись отключена");
        emailVerificationService.verify(user, request.code());
        userRepository.save(user);
        return issueNewSession(user, ip, userAgent);
    }

    @Transactional
    public AuthDtos.VerificationActionResponse resendVerification(AuthDtos.ResendVerificationRequest request) {
        String email = normalizeEmail(request.email());
        AppUser user = userRepository.findByEmailIgnoreCaseForUpdate(email)
                .orElseThrow(() -> new BusinessException("Пользователь не найден"));
        if (!user.isEnabled()) throw new BusinessException("Учётная запись отключена");
        if (!user.isEmailVerified()) {
            emailVerificationService.resendCode(user);
            userRepository.save(user);
        }
        return new AuthDtos.VerificationActionResponse(email, emailVerificationService.resendAfterSeconds(),
                user.isEmailVerified() ? "Email уже подтверждён" : "Новый код отправлен на email");
    }

    public AuthDtos.AuthResponse refresh(AppUser user) {
        if (!user.isEmailVerified()) throw new EmailVerificationRequiredException(user.getEmail());
        String sid = currentUserService.currentSessionId();
        JwtService.TokenResult token = jwtService.issue(user, sid);
        return response(user, token);
    }

    public AuthDtos.UserView toView(AppUser user) {
        return new AuthDtos.UserView(user.getId(), user.getFullName(), user.getEmail(), Set.copyOf(user.getRoles()), Set.copyOf(user.getPermissions()));
    }

    private AuthDtos.AuthResponse issueNewSession(AppUser user, String ip, String userAgent) {
        var session = sessionService.create(user, ip, userAgent);
        JwtService.TokenResult token = jwtService.issue(user, session.getId());
        return response(user, token);
    }

    private AuthDtos.AuthResponse response(AppUser user, JwtService.TokenResult token) {
        return new AuthDtos.AuthResponse(token.token(), "Bearer", token.expiresInSeconds(), toView(user));
    }

    private boolean isReservedSuperAdminEmail(String email) { return configuredSuperAdmins().contains(normalizeEmail(email)); }

    private Set<String> configuredSuperAdmins() {
        return Arrays.stream(superAdminEmails.split(",")).map(this::normalizeEmail).filter(value -> !value.isBlank()).collect(Collectors.toSet());
    }

    private String normalizeEmail(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
}
