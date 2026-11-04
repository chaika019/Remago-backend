package com.adrotation.crm.crm.controller;

import com.adrotation.crm.crm.dto.SavedViewDtos;
import com.adrotation.crm.crm.service.SavedViewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/saved-views")
@RequiredArgsConstructor
public class SavedViewController {
    private final SavedViewService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER')")
    public List<SavedViewDtos.Response> list(@RequestParam(defaultValue = "CRM") String module) { return service.list(module); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER')")
    public SavedViewDtos.Response create(@Valid @RequestBody SavedViewDtos.SaveRequest request) { return service.create(request); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER')")
    public void delete(@PathVariable Long id) { service.delete(id); }
}
