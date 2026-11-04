package com.adrotation.crm.security.service;

import com.adrotation.crm.security.entity.TwoFactorChallenge;
import com.adrotation.crm.security.repository.TwoFactorChallengeRepository;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TwoFactorService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TwoFactorChallengeRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.security.admin-2fa.enabled:true}") private boolean enabled;
    @Value("${app.security.admin-2fa.ttl-minutes:10}") private long ttlMinutes;
    @Value("${app.security.admin-2fa.max-attempts:5}") private int maxAttempts;
    @Value("${spring.mail.username:}") private String mailUsername;
    @Value("${app.delivery.email-from:}") private String emailFrom;

    public boolean enabled() { return enabled; }
    public long expiresInSeconds() { return Duration.ofMinutes(ttlMinutes).toSeconds(); }

    @Transactional
    public TwoFactorChallenge issue(AppUser user, String ip, String userAgent) {
        if (!enabled) throw new BusinessException("Двухфакторная защита SUPER_ADMIN отключена сервером");
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || mailUsername == null || mailUsername.isBlank()) {
            throw new BusinessException("Для входа SUPER_ADMIN требуется SMTP. Настройте MAIL_USERNAME и MAIL_PASSWORD.");
        }
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        Instant now = Instant.now();
        TwoFactorChallenge challenge = new TwoFactorChallenge();
        challenge.setId(UUID.randomUUID().toString().replace("-", ""));
        challenge.setUser(user);
        challenge.setCodeHash(passwordEncoder.encode(code));
        challenge.setExpiresAt(now.plus(Duration.ofMinutes(ttlMinutes)));
        challenge.setAttempts(0);
        challenge.setIpAddress(trim(ip, 100));
        challenge.setUserAgent(trim(userAgent, 1000));
        repository.save(challenge);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailFrom != null && !emailFrom.isBlank() ? emailFrom.trim() : mailUsername.trim());
            message.setTo(user.getEmail());
            message.setSubject("REMAGO FLOW — подтверждение входа");
            message.setText("Код подтверждения входа: " + code + "\n\nКод действует " + ttlMinutes + " минут. Никому его не сообщайте.");
            sender.send(message);
        } catch (RuntimeException ex) {
            repository.delete(challenge);
            throw new BusinessException("Не удалось отправить код двухфакторной аутентификации");
        }
        return challenge;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AppUser verify(String challengeId, String code) {
        TwoFactorChallenge challenge = repository.findById(challengeId)
                .orElseThrow(() -> new BusinessException("Запрос подтверждения входа не найден"));
        if (challenge.getConsumedAt() != null) throw new BusinessException("Код уже использован");
        if (Instant.now().isAfter(challenge.getExpiresAt())) throw new BusinessException("Срок действия кода истёк. Войдите снова.");
        if (challenge.getAttempts() >= maxAttempts) throw new BusinessException("Превышено число попыток. Войдите снова.");
        if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            repository.save(challenge);
            throw new BusinessException("Неверный код. Осталось попыток: " + Math.max(0, maxAttempts - challenge.getAttempts()));
        }
        challenge.setConsumedAt(Instant.now());
        repository.save(challenge);
        return challenge.getUser();
    }

    public String mask(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] p = email.split("@", 2);
        String local = p[0];
        String shown = local.length() <= 2 ? local.charAt(0) + "***" : local.substring(0, 2) + "***";
        return shown + "@" + p[1];
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
