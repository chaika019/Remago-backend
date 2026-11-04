package com.adrotation.crm.crm.controller;

import com.adrotation.crm.crm.dto.DealDtos;
import com.adrotation.crm.crm.service.DealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deals")
@RequiredArgsConstructor
public class DealController {
    private final DealService dealService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER')")
    public List<DealDtos.DealResponse> list(@RequestParam(defaultValue = "2026") int year,
                                            @RequestParam("week") int weekNumber,
                                            @RequestParam(required = false, name = "manager") Long managerId) {
        return dealService.list(year, weekNumber, managerId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER')")
    public DealDtos.DealResponse get(@PathVariable Long id) {
        return dealService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public DealDtos.DealResponse create(@Valid @RequestBody DealDtos.CreateDealRequest request) {
        return dealService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public DealDtos.DealResponse update(@PathVariable Long id, @RequestBody DealDtos.UpdateDealRequest request) {
        return dealService.update(id, request);
    }
}
