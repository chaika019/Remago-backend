package com.adrotation.crm.city.controller;

import com.adrotation.crm.city.dto.CityDtos;
import com.adrotation.crm.city.enums.CityCategory;
import com.adrotation.crm.city.service.CityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cities")
@RequiredArgsConstructor
public class CityController {
    private final CityService cityService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER','LAWYER','SHIFT_WORKER','BUH','DELO')")
    public List<CityDtos.CityResponse> list(@RequestParam(required = false) CityCategory category,
                                            @RequestParam(required = false) Boolean needsRepair,
                                            @RequestParam(required = false) String query) {
        return cityService.list(category, needsRepair, query);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ROP','ROK')")
    public CityDtos.CityResponse create(@Valid @RequestBody CityDtos.CityRequest request) {
        return cityService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ROP','ROK')")
    public CityDtos.CityResponse update(@PathVariable Long id, @Valid @RequestBody CityDtos.CityRequest request) {
        return cityService.update(id, request);
    }
}
