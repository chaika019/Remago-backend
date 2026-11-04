package com.adrotation.crm.auth.security;

import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.user.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtEncoder jwtEncoder;

    @Value("${app.security.token-ttl-minutes:480}")
    private long ttlMinutes;

    public TokenResult issue(AppUser user, String sessionId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(ttlMinutes));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ad-crm-backend")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getEmail())
                .claim("sid", sessionId)
                .claim("userId", user.getId())
                .claim("name", user.getFullName())
                .claim("roles", user.getRoles().stream().map(Role::name).toList())
                .claim("permissions", user.getPermissions().stream().map(Enum::name).toList())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResult(token, Duration.between(now, expiresAt).toSeconds());
    }

    public record TokenResult(String token, long expiresInSeconds) {}
}
