package com.adrotation.crm.crm.service;

import com.adrotation.crm.auth.security.CurrentUserService;
import com.adrotation.crm.crm.dto.SavedViewDtos;
import com.adrotation.crm.crm.entity.SavedView;
import com.adrotation.crm.crm.repository.SavedViewRepository;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.shared.exception.NotFoundException;
import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.user.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SavedViewService {
    private final SavedViewRepository repository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<SavedViewDtos.Response> list(String module) {
        AppUser user = currentUserService.requireCurrentUser();
        String normalized = normalizeModule(module);
        return repository.findVisible(normalized, user.getId())
                .stream().map(this::dto).toList();
    }

    @Transactional
    public SavedViewDtos.Response create(SavedViewDtos.SaveRequest request) {
        AppUser user = currentUserService.requireCurrentUser();
        String module = normalizeModule(request.module());
        String name = request.name().trim();
        if (repository.existsByUser_IdAndModuleAndNameIgnoreCase(user.getId(), module, name)) {
            throw new BusinessException("Представление с таким названием уже существует");
        }
        if (request.shared() && !canShare(user)) {
            throw new BusinessException("Общие представления может создавать только руководитель");
        }
        SavedView view = new SavedView();
        view.setUser(user);
        view.setModule(module);
        view.setName(name);
        view.setFiltersJson(request.filtersJson());
        view.setShared(request.shared());
        return dto(repository.save(view));
    }

    @Transactional
    public void delete(Long id) {
        AppUser user = currentUserService.requireCurrentUser();
        SavedView view = repository.findById(id).orElseThrow(() -> new NotFoundException("Представление не найдено: " + id));
        if (!view.getUser().getId().equals(user.getId()) && !user.getRoles().contains(Role.SUPER_ADMIN)) {
            throw new BusinessException("Можно удалить только своё представление");
        }
        repository.delete(view);
    }

    private boolean canShare(AppUser user) {
        return user.getRoles().stream().anyMatch(r -> r == Role.SUPER_ADMIN || r == Role.DIRECTOR || r == Role.ROP || r == Role.ROK);
    }

    private String normalizeModule(String module) {
        String value = module == null ? "CRM" : module.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9_-]{1,50}")) throw new BusinessException("Некорректный модуль представления");
        return value;
    }

    private SavedViewDtos.Response dto(SavedView view) {
        return new SavedViewDtos.Response(view.getId(), view.getName(), view.getModule(), view.getFiltersJson(), view.isShared(),
                view.getUser().getId(), view.getUser().getFullName(), view.getCreatedAt());
    }
}
