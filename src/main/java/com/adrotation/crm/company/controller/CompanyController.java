package com.adrotation.crm.company.controller;

import com.adrotation.crm.company.dto.CompanyDtos;
import com.adrotation.crm.company.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {
    private final CompanyService companyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER','BUH','DELO')")
    public List<CompanyDtos.CompanyResponse> search(@RequestParam(defaultValue = "") String query) {
        return companyService.search(query);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER','BUH','DELO')")
    public CompanyDtos.CompanyResponse get(@PathVariable Long id) {
        return companyService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ROP','ROK','MANAGER','BUH','DELO')")
    public CompanyDtos.CompanyResponse create(@Valid @RequestBody CompanyDtos.CompanyCreateRequest request) {
        return companyService.create(request);
    }


    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ROP','ROK')")
    public CompanyDtos.CompanyResponse update(@PathVariable Long id, @RequestBody CompanyDtos.CompanyUpdateRequest request) {
        return companyService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ROP','ROK','MANAGER','BUH','DELO')")
    public CompanyDtos.CompanyResponse updateStatus(@PathVariable Long id, @Valid @RequestBody CompanyDtos.CompanyStatusRequest request) {
        return companyService.updateStatus(id, request.status());
    }

    @GetMapping("/{companyId}/contacts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER','BUH','DELO')")
    public List<CompanyDtos.ContactResponse> contacts(@PathVariable Long companyId) {
        return companyService.contacts(companyId);
    }

    @PostMapping("/{companyId}/contacts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ROP','ROK','MANAGER','BUH','DELO')")
    public CompanyDtos.ContactResponse addContact(@PathVariable Long companyId,
                                                   @Valid @RequestBody CompanyDtos.ContactRequest request) {
        return companyService.addContact(companyId, request);
    }
    @PutMapping("/contacts/{contactId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ROP','ROK','MANAGER','BUH','DELO')")
    public CompanyDtos.ContactResponse updateContact(@PathVariable Long contactId,
                                                      @Valid @RequestBody CompanyDtos.ContactRequest request) {
        return companyService.updateContact(contactId, request);
    }

    @DeleteMapping("/contacts/{contactId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ROP','ROK','MANAGER','BUH','DELO')")
    public void deleteContact(@PathVariable Long contactId) {
        companyService.deleteContact(contactId);
    }

}
