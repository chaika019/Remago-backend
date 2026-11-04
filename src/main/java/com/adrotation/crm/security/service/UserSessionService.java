package com.adrotation.crm.security.service;

import com.adrotation.crm.security.dto.SecurityDtos;
import com.adrotation.crm.security.entity.UserSession;
import com.adrotation.crm.security.repository.UserSessionRepository;
import com.adrotation.crm.shared.exception.NotFoundException;
import com.adrotation.crm.shared.exception.UnauthorizedException;
import com.adrotation.crm.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserSessionService {
    private final UserSessionRepository repository;

    @Value("${app.security.token-ttl-minutes:480}")
    private long ttlMinutes;

    @Transactional
    public UserSession create(AppUser user, String ip, String userAgent) {
        Instant now = Instant.now();
        UserSession session = new UserSession();
        session.setId(UUID.randomUUID().toString().replace("-", ""));
        session.setUser(user);
        session.setIpAddress(trim(ip, 100));
        session.setUserAgent(trim(userAgent, 1000));
        session.setCreatedAt(now);
        session.setLastSeenAt(now);
        session.setExpiresAt(now.plus(Duration.ofMinutes(ttlMinutes)));
        return repository.save(session);
    }

    @Transactional
    public void validateAndTouch(String sessionId, String email) {
        if (sessionId == null || sessionId.isBlank()) throw new UnauthorizedException("Токен не связан с активной сессией");
        UserSession session = repository.findById(sessionId).orElseThrow(() -> new UnauthorizedException("Сессия завершена"));
        Instant now = Instant.now();
        if (!session.activeAt(now) || !session.getUser().isEnabled() || !session.getUser().getEmail().equalsIgnoreCase(email)) {
            throw new UnauthorizedException("Сессия завершена или истекла");
        }
        if (Duration.between(session.getLastSeenAt(), now).toMinutes() >= 5) {
            session.setLastSeenAt(now);
            repository.save(session);
        }
    }

    @Transactional(readOnly = true)
    public List<SecurityDtos.SessionView> list(AppUser user, String currentSessionId) {
        return repository.findByUser_IdOrderByLastSeenAtDesc(user.getId()).stream()
                .map(s -> new SecurityDtos.SessionView(s.getId(), s.getIpAddress(), s.getUserAgent(), s.getCreatedAt(), s.getLastSeenAt(),
                        s.getExpiresAt(), s.getId().equals(currentSessionId), s.getRevokedAt() != null || !s.getExpiresAt().isAfter(Instant.now())))
                .toList();
    }

    @Transactional
    public void revoke(AppUser user, String sessionId) {
        UserSession s = repository.findById(sessionId).orElseThrow(() -> new NotFoundException("Сессия не найдена"));
        if (!s.getUser().getId().equals(user.getId())) throw new NotFoundException("Сессия не найдена");
        if (s.getRevokedAt() == null) { s.setRevokedAt(Instant.now()); repository.save(s); }
    }

    @Transactional
    public int revokeAll(AppUser user, String exceptSessionId) {
        if (exceptSessionId == null || exceptSessionId.isBlank()) return repository.revokeAll(user.getId(), Instant.now());
        return repository.revokeAllExcept(user.getId(), exceptSessionId, Instant.now());
    }

    @Transactional
    public void revokeCurrent(String sessionId) {
        repository.findById(sessionId).ifPresent(s -> { if (s.getRevokedAt() == null) { s.setRevokedAt(Instant.now()); repository.save(s); } });
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        String v = value.trim(); return v.length() <= max ? v : v.substring(0, max);
    }
}
