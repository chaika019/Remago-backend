package com.adrotation.crm.config;

import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.user.enums.Role;
import com.adrotation.crm.user.repository.AppUserRepository;
import com.adrotation.crm.user.service.UserAccessPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminBootstrapConfig implements ApplicationRunner {

    private static final String EMAIL = "egorchuiko7@gmail.com";
    private static final String FULL_NAME = "Егор Чуйко";
    private static final String PASSWORD = "123123123123";

    private final AppUserRepository repository;
    private final UserAccessPolicy accessPolicy;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        AppUser user = repository
                .findByEmailIgnoreCase(EMAIL)
                .orElseGet(AppUser::new);

        user.setFullName(FULL_NAME);
        user.setEmail(EMAIL);

        // DEV: принудительно сбрасываем пароль при каждом старте backend
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));

        user.setEnabled(true);
        user.setEmailVerified(true);

        user.setRoles(EnumSet.of(Role.SUPER_ADMIN));
        user.setPermissions(
                accessPolicy.permissionsFor(user.getRoles())
        );

        repository.save(user);

        log.warn(
                "DEV MODE: SUPER_ADMIN {} password was reset to hardcoded development password",
                EMAIL
        );
    }
}