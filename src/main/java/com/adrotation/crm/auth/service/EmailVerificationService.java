package com.adrotation.crm.auth.service;

import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.email-verification.enabled:true}")
    private boolean enabled;

    @Value("${app.email-verification.code-ttl-minutes:10}")
    private long codeTtlMinutes;

    @Value("${app.email-verification.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;

    @Value("${app.email-verification.max-attempts:5}")
    private int maxAttempts;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.delivery.email-from:}")
    private String emailFrom;

    public long codeExpiresInSeconds() {
        return Duration.ofMinutes(codeTtlMinutes).toSeconds();
    }

    public long resendAfterSeconds() {
        return resendCooldownSeconds;
    }

    public void sendInitialCode(AppUser user) {
        issueAndSend(user, true);
    }

    public void resendCode(AppUser user) {
        if (user.isEmailVerified()) {
            return;
        }

        Instant lastSent = user.getEmailVerificationLastSentAt();
        if (lastSent != null) {
            long elapsed = Duration.between(lastSent, Instant.now()).getSeconds();
            if (elapsed < resendCooldownSeconds) {
                long wait = Math.max(1, resendCooldownSeconds - elapsed);
                throw new BusinessException("Повторно отправить код можно через " + wait + " сек.");
            }
        }

        issueAndSend(user, false);
    }

    public void verify(AppUser user, String rawCode) {
        if (user.isEmailVerified()) {
            throw new BusinessException("Email уже подтверждён. Войдите с помощью пароля.");
        }

        if (user.getEmailVerificationCodeHash() == null || user.getEmailVerificationExpiresAt() == null) {
            throw new BusinessException("Код подтверждения не найден. Запросите новый код.");
        }

        if (Instant.now().isAfter(user.getEmailVerificationExpiresAt())) {
            clearCode(user);
            throw new BusinessException("Срок действия кода истёк. Запросите новый код.");
        }

        if (user.getEmailVerificationAttempts() >= maxAttempts) {
            clearCode(user);
            throw new BusinessException("Превышено число попыток. Запросите новый код.");
        }

        if (!passwordEncoder.matches(rawCode, user.getEmailVerificationCodeHash())) {
            user.setEmailVerificationAttempts(user.getEmailVerificationAttempts() + 1);
            int remaining = Math.max(0, maxAttempts - user.getEmailVerificationAttempts());
            if (remaining == 0) {
                clearCode(user);
                throw new BusinessException("Неверный код. Попытки закончились — запросите новый код.");
            }
            throw new BusinessException("Неверный код. Осталось попыток: " + remaining);
        }

        user.setEmailVerified(true);
        clearCode(user);
    }

    private void issueAndSend(AppUser user, boolean ignoreCooldown) {
        if (!enabled) {
            throw new BusinessException("Подтверждение email отключено на сервере. Обратитесь к администратору.");
        }

        if (!ignoreCooldown) {
            Instant lastSent = user.getEmailVerificationLastSentAt();
            if (lastSent != null) {
                long elapsed = Duration.between(lastSent, Instant.now()).getSeconds();
                if (elapsed < resendCooldownSeconds) {
                    long wait = Math.max(1, resendCooldownSeconds - elapsed);
                    throw new BusinessException("Повторно отправить код можно через " + wait + " сек.");
                }
            }
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || mailUsername == null || mailUsername.isBlank()) {
            throw new BusinessException(
                    "Отправка кодов по email не настроена. Укажите MAIL_USERNAME и MAIL_PASSWORD в .env"
            );
        }

        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        Instant now = Instant.now();

        user.setEmailVerificationCodeHash(passwordEncoder.encode(code));
        user.setEmailVerificationExpiresAt(now.plus(Duration.ofMinutes(codeTtlMinutes)));
        user.setEmailVerificationAttempts(0);
        user.setEmailVerificationLastSentAt(now);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String from = emailFrom != null && !emailFrom.isBlank() ? emailFrom.trim() : mailUsername.trim();
            message.setFrom(from);
            message.setTo(user.getEmail());
            message.setSubject("REMAGO FLOW — код подтверждения");
            message.setText(
                    "Ваш код подтверждения REMAGO FLOW: " + code + "\n\n"
                            + "Код действует " + codeTtlMinutes + " минут.\n"
                            + "Если вы не регистрировались в REMAGO FLOW, просто проигнорируйте это письмо."
            );
            sender.send(message);
        } catch (RuntimeException ex) {
            clearCode(user);
            throw new BusinessException("Не удалось отправить код на email. Проверьте SMTP-настройки.");
        }
    }

    private void clearCode(AppUser user) {
        user.setEmailVerificationCodeHash(null);
        user.setEmailVerificationExpiresAt(null);
        user.setEmailVerificationAttempts(0);
    }
}
