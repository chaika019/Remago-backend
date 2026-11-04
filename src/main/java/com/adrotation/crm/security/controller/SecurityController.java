package com.adrotation.crm.security.controller;

import com.adrotation.crm.auth.security.CurrentUserService;
import com.adrotation.crm.security.dto.SecurityDtos;
import com.adrotation.crm.security.service.AuthAttemptService;
import com.adrotation.crm.security.service.UserSessionService;
import com.adrotation.crm.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
public class SecurityController {
    private final CurrentUserService currentUserService;
    private final UserSessionService sessionService;
    private final AuthAttemptService authAttemptService;

    @GetMapping("/sessions")
    public SecurityDtos.SessionsResponse sessions() {
        AppUser user = currentUserService.requireCurrentUser();
        return new SecurityDtos.SessionsResponse(sessionService.list(user, currentUserService.currentSessionId()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable String sessionId) { sessionService.revoke(currentUserService.requireCurrentUser(), sessionId); }

    @PostMapping("/sessions/revoke-all")
    public int revokeAll(@RequestParam(defaultValue = "true") boolean keepCurrent) {
        AppUser user = currentUserService.requireCurrentUser();
        return sessionService.revokeAll(user, keepCurrent ? currentUserService.currentSessionId() : null);
    }

    @GetMapping("/auth-attempts")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<SecurityDtos.AuthAttemptView> attempts(@RequestParam(defaultValue = "100") int limit) { return authAttemptService.recent(limit); }
}
