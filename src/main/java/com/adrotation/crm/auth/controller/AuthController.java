package com.adrotation.crm.auth.controller;

import com.adrotation.crm.auth.dto.AuthDtos;
import com.adrotation.crm.auth.security.CurrentUserService;
import com.adrotation.crm.auth.service.AuthService;
import com.adrotation.crm.security.service.ClientIpResolver;
import com.adrotation.crm.security.service.RateLimitService;
import com.adrotation.crm.security.service.UserSessionService;
import com.adrotation.crm.user.entity.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final CurrentUserService currentUserService;
    private final ClientIpResolver clientIpResolver;
    private final RateLimitService rateLimitService;
    private final UserSessionService sessionService;

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletRequest servletRequest) {
        String ip = clientIpResolver.resolve(servletRequest);
        rateLimitService.login(ip + ':' + request.email().trim().toLowerCase());
        return ResponseEntity.ok(authService.login(request, ip, servletRequest.getHeader("User-Agent")));
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<AuthDtos.AuthResponse> verifySecondFactor(@Valid @RequestBody AuthDtos.TwoFactorVerifyRequest request, HttpServletRequest servletRequest) {
        String ip = clientIpResolver.resolve(servletRequest);
        rateLimitService.code(ip + ':' + request.challengeId());
        return ResponseEntity.ok(authService.verifySecondFactor(request, ip, servletRequest.getHeader("User-Agent")));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.RegisterResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request, HttpServletRequest servletRequest) {
        String ip = clientIpResolver.resolve(servletRequest);
        rateLimitService.register(ip + ':' + request.email().trim().toLowerCase());
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<AuthDtos.AuthResponse> verifyEmail(@Valid @RequestBody AuthDtos.VerifyEmailRequest request, HttpServletRequest servletRequest) {
        String ip = clientIpResolver.resolve(servletRequest);
        rateLimitService.code(ip + ':' + request.email().trim().toLowerCase());
        return ResponseEntity.ok(authService.verifyEmail(request, ip, servletRequest.getHeader("User-Agent")));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<AuthDtos.VerificationActionResponse> resendVerification(@Valid @RequestBody AuthDtos.ResendVerificationRequest request, HttpServletRequest servletRequest) {
        String ip = clientIpResolver.resolve(servletRequest);
        rateLimitService.code(ip + ':' + request.email().trim().toLowerCase());
        return ResponseEntity.ok(authService.resendVerification(request));
    }

    @GetMapping("/me")
    public AuthDtos.UserView me() { return authService.toView(currentUserService.requireCurrentUser()); }

    @PostMapping("/refresh")
    public AuthDtos.AuthResponse refresh() { return authService.refresh(currentUserService.requireCurrentUser()); }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() { sessionService.revokeCurrent(currentUserService.currentSessionId()); }
}
