package com.adrotation.crm.auth.service;

import com.adrotation.crm.auth.dto.AuthDtos;
import com.adrotation.crm.auth.security.CurrentUserService;
import com.adrotation.crm.auth.security.JwtService;
import com.adrotation.crm.security.entity.UserSession;
import com.adrotation.crm.security.service.AuthAttemptService;
import com.adrotation.crm.security.service.TwoFactorService;
import com.adrotation.crm.security.service.UserSessionService;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.user.enums.Role;
import com.adrotation.crm.user.repository.AppUserRepository;
import com.adrotation.crm.user.service.UserAccessPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailVerificationAuthenticationTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JwtService jwt = mock(JwtService.class);
    private final UserSessionService sessions = mock(UserSessionService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> mail = mock(ObjectProvider.class);
    private final EmailVerificationService verification = new EmailVerificationService(mail, passwords);
    private final AuthService auth = new AuthService(users, passwords, jwt, new UserAccessPolicy(),
            verification, mock(TwoFactorService.class), sessions, mock(AuthAttemptService.class),
            mock(CurrentUserService.class));

    private AppUser user(Role role, boolean verified) {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setEmail("user@example.test");
        user.setEnabled(true);
        user.setEmailVerified(verified);
        user.setRoles(Set.of(role));
        user.setPermissions(Set.of());
        when(users.findByEmailIgnoreCaseForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        ReflectionTestUtils.setField(verification, "maxAttempts", 5);
        return user;
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void alreadyVerifiedAccountCannotObtainSessionWithArbitraryCode(Role role) {
        AppUser user = user(role, true);
        UserSession session = new UserSession();
        session.setId("session");
        when(sessions.create(user, "127.0.0.1", "test")).thenReturn(session);
        when(jwt.issue(user, "session")).thenReturn(new JwtService.TokenResult("token", 60));
        assertThrows(BusinessException.class, () -> auth.verifyEmail(
                new AuthDtos.VerifyEmailRequest(user.getEmail(), "000000"), "127.0.0.1", "test"));
        verifyNoInteractions(sessions, jwt, passwords);
    }

    @Test
    void validRegistrationCodeWorksOnceAndCannotBeReplayed() {
        AppUser user = user(Role.PENDING, false);
        user.setEmailVerificationCodeHash("hash");
        user.setEmailVerificationExpiresAt(Instant.now().plusSeconds(60));
        when(passwords.matches("123456", "hash")).thenReturn(true);
        UserSession session = new UserSession();
        session.setId("session");
        when(sessions.create(user, "127.0.0.1", "test")).thenReturn(session);
        when(jwt.issue(user, "session")).thenReturn(new JwtService.TokenResult("token", 60));
        var request = new AuthDtos.VerifyEmailRequest(user.getEmail(), "123456");

        assertEquals("token", auth.verifyEmail(request, "127.0.0.1", "test").accessToken());
        assertTrue(user.isEmailVerified());
        assertNull(user.getEmailVerificationCodeHash());
        assertThrows(BusinessException.class, () -> auth.verifyEmail(request, "127.0.0.1", "test"));
        verify(sessions, times(1)).create(any(), any(), any());
        verify(jwt, times(1)).issue(any(), any());
    }

    @Test
    void invalidCodeDoesNotIssueSession() {
        AppUser user = user(Role.PENDING, false);
        user.setEmailVerificationCodeHash("hash");
        user.setEmailVerificationExpiresAt(Instant.now().plusSeconds(60));
        assertThrows(BusinessException.class, () -> auth.verifyEmail(
                new AuthDtos.VerifyEmailRequest(user.getEmail(), "000000"), "127.0.0.1", "test"));
        assertFalse(user.isEmailVerified());
        assertEquals(1, user.getEmailVerificationAttempts());
        verifyNoInteractions(sessions, jwt);
    }
}
