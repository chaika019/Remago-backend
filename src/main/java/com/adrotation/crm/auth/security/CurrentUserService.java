package com.adrotation.crm.auth.security;

import com.adrotation.crm.shared.exception.UnauthorizedException;
import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final AppUserRepository userRepository;

    public AppUser requireCurrentUser() {
        Authentication authentication = authentication();
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new UnauthorizedException("Пользователь из токена отключён или больше не существует"));
    }

    public String currentSessionId() {
        Authentication authentication = authentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String sid = jwt.getToken().getClaimAsString("sid");
            if (sid != null && !sid.isBlank()) return sid;
        }
        throw new UnauthorizedException("Сессия пользователя не определена");
    }

    private Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Пользователь не авторизован");
        }
        return authentication;
    }
}
