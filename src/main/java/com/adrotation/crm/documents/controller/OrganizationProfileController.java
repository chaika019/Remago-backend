package com.adrotation.crm.documents.controller;

import com.adrotation.crm.documents.dto.OrganizationProfileDtos;
import com.adrotation.crm.documents.service.OrganizationProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organization-profile")
@RequiredArgsConstructor
public class OrganizationProfileController {
    private final OrganizationProfileService service;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public OrganizationProfileDtos.Response get() { return service.get(); }

    @PatchMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public OrganizationProfileDtos.Response update(@RequestBody OrganizationProfileDtos.UpdateRequest request) {
        return service.update(request);
    }
}
