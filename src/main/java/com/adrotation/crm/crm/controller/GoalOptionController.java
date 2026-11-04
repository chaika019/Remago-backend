package com.adrotation.crm.crm.controller;

import com.adrotation.crm.crm.dto.GoalOptionDto;
import com.adrotation.crm.crm.dto.GoalOptionUpdateRequest;
import com.adrotation.crm.crm.service.GoalOptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/goal-options")
@RequiredArgsConstructor
public class GoalOptionController {
    private final GoalOptionService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER')")
    public List<GoalOptionDto> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public GoalOptionDto create(@RequestBody Map<String, String> body) {
        return service.create(body.get("name"));
    }
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public GoalOptionDto update(@PathVariable Long id, @RequestBody GoalOptionUpdateRequest request) {
        return service.update(id, request);
    }

}
