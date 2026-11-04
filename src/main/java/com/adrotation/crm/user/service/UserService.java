package com.adrotation.crm.user.service;

import com.adrotation.crm.auth.dto.AuthDtos;
import com.adrotation.crm.auth.security.CurrentUserService;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.shared.exception.NotFoundException;
import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.user.enums.Role;
import com.adrotation.crm.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository repository;
    private final CurrentUserService currentUserService;
    private final UserAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public List<AuthDtos.UserView> list(Role role) {
        AppUser current = currentUserService.requireCurrentUser();
        boolean superAdmin = current.getRoles().contains(Role.SUPER_ADMIN);
        boolean rok = current.getRoles().contains(Role.ROK);

        if (!superAdmin && !rok) {
            throw new BusinessException("Недостаточно прав для управления сотрудниками");
        }

        List<AppUser> users = role == null
                ? repository.findAll()
                : repository.findEnabledByRole(role);

        return users.stream()
                .filter(AppUser::isEnabled)
                .filter(user -> superAdmin || user.getRoles().contains(Role.PENDING))
                .map(this::toView)
                .toList();
    }

    @Transactional
    public AuthDtos.UserView assignRoles(Long userId, Set<Role> requestedRoles) {
        AppUser current = currentUserService.requireCurrentUser();
        AppUser target = repository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден: " + userId));

        Set<Role> roles = normalizeRoles(requestedRoles);
        boolean superAdmin = current.getRoles().contains(Role.SUPER_ADMIN);
        boolean rok = current.getRoles().contains(Role.ROK);

        if (superAdmin) {
            if (current.getId().equals(target.getId()) && !roles.contains(Role.SUPER_ADMIN)) {
                throw new BusinessException("Нельзя снять роль SUPER_ADMIN с собственной учётной записи");
            }
            target.setRoles(roles);
        } else if (rok) {
            if (!target.getRoles().contains(Role.PENDING)) {
                throw new BusinessException("РОК может назначить роль только новому пользователю в ожидании");
            }
            if (!roles.equals(EnumSet.of(Role.MANAGER))) {
                throw new BusinessException("РОК может назначить новому пользователю только роль MANAGER");
            }
            target.setRoles(EnumSet.of(Role.MANAGER));
        } else {
            throw new BusinessException("Недостаточно прав для назначения ролей");
        }

        target.setPermissions(accessPolicy.permissionsFor(target.getRoles()));
        return toView(repository.save(target));
    }

    @Transactional
    public AuthDtos.UserView disable(Long userId) {
        AppUser current = currentUserService.requireCurrentUser();
        if (!current.getRoles().contains(Role.SUPER_ADMIN)) {
            throw new BusinessException("Отключать пользователей может только супер-администратор");
        }
        if (current.getId().equals(userId)) {
            throw new BusinessException("Нельзя отключить собственную учётную запись");
        }

        AppUser target = repository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден: " + userId));
        target.setEnabled(false);
        return toView(repository.save(target));
    }

    public AuthDtos.UserView toView(AppUser user) {
        return new AuthDtos.UserView(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                Set.copyOf(user.getRoles()),
                Set.copyOf(user.getPermissions())
        );
    }

    private Set<Role> normalizeRoles(Set<Role> requestedRoles) {
        if (requestedRoles == null || requestedRoles.isEmpty()) {
            throw new BusinessException("Укажите хотя бы одну роль");
        }
        EnumSet<Role> roles = EnumSet.copyOf(requestedRoles);
        if (roles.contains(Role.PENDING) && roles.size() > 1) {
            throw new BusinessException("Роль PENDING нельзя совмещать с рабочими ролями");
        }
        return roles;
    }
}
