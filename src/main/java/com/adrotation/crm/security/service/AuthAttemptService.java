package com.adrotation.crm.security.service;

import com.adrotation.crm.security.dto.SecurityDtos;
import com.adrotation.crm.security.entity.AuthAttempt;
import com.adrotation.crm.security.repository.AuthAttemptRepository;
import com.adrotation.crm.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthAttemptService {
    private final AuthAttemptRepository repository;

    @Transactional
    public void record(AppUser user, String email, String eventType, boolean success, String ip, String userAgent, String reason) {
        AuthAttempt a = new AuthAttempt();
        a.setUser(user);
        a.setEmail(email == null ? null : email.toLowerCase());
        a.setEventType(eventType);
        a.setSuccess(success);
        a.setIpAddress(trim(ip, 100));
        a.setUserAgent(trim(userAgent, 1000));
        a.setFailureReason(trim(reason, 500));
        repository.save(a);
    }

    @Transactional(readOnly = true)
    public List<SecurityDtos.AuthAttemptView> recent(int limit) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.max(1, Math.min(limit, 200))))
                .stream().map(this::dto).toList();
    }

    private SecurityDtos.AuthAttemptView dto(AuthAttempt a) {
        return new SecurityDtos.AuthAttemptView(a.getId(), a.getUser() == null ? null : a.getUser().getId(), a.getEmail(),
                a.getEventType(), a.isSuccess(), a.getIpAddress(), a.getUserAgent(), a.getFailureReason(), a.getCreatedAt());
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
