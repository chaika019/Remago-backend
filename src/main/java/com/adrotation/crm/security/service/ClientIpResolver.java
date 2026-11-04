package com.adrotation.crm.security.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ClientIpResolver {
    @Value("${app.security.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    @Value("${app.security.trusted-proxy-addresses:127.0.0.1,::1}")
    private String trustedProxyAddresses;

    public String resolve(HttpServletRequest request) {
        String remote = safe(request.getRemoteAddr());
        if (!trustProxyHeaders || !trustedProxies().contains(remote)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return remote;
        String first = forwarded.split(",")[0].trim();
        return first.matches("[0-9a-fA-F:.]{2,100}") ? first : remote;
    }

    private Set<String> trustedProxies() {
        return Arrays.stream(trustedProxyAddresses.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet());
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.length() > 100 ? value.substring(0, 100) : value;
    }
}
