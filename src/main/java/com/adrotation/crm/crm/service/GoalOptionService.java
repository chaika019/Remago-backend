package com.adrotation.crm.crm.service;

import com.adrotation.crm.crm.dto.GoalOptionDto;
import com.adrotation.crm.crm.dto.GoalOptionUpdateRequest;
import com.adrotation.crm.crm.entity.GoalOption;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.shared.exception.NotFoundException;
import com.adrotation.crm.crm.repository.GoalOptionRepository;
import com.adrotation.crm.shared.service.DtoMapper;
import com.adrotation.crm.shared.service.TextSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GoalOptionService {
    private final GoalOptionRepository repository;
    private final TextSanitizer sanitizer;
    private final DtoMapper mapper;

    public GoalOption require(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Цель дня не найдена: " + id));
    }

    public List<GoalOptionDto> list() {
        return repository.findByActiveTrueOrderByNameAsc().stream().map(mapper::goal).toList();
    }

    @Transactional
    public GoalOptionDto create(String rawName) {
        String name = sanitizer.plain(rawName);
        if (name == null || name.isBlank()) throw new BusinessException("Название цели не может быть пустым");
        if (repository.existsByNameIgnoreCase(name)) throw new BusinessException("Такая цель уже существует");
        GoalOption goal = new GoalOption();
        goal.setName(name);
        goal.setActive(true);
        return mapper.goal(repository.save(goal));
    }
    @Transactional
    public GoalOptionDto update(Long id, GoalOptionUpdateRequest request) {
        GoalOption goal = require(id);
        if (request.name() != null) {
            String name = sanitizer.plain(request.name());
            if (name == null || name.isBlank()) throw new BusinessException("Название цели не может быть пустым");
            if (!name.equalsIgnoreCase(goal.getName()) && repository.existsByNameIgnoreCase(name)) {
                throw new BusinessException("Такая цель уже существует");
            }
            goal.setName(name);
        }
        if (request.active() != null) goal.setActive(request.active());
        return mapper.goal(repository.save(goal));
    }

}
