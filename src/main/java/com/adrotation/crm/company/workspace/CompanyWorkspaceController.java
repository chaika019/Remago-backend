package com.adrotation.crm.company.workspace;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyWorkspaceController {
    private final CompanyWorkspaceService service;

    @GetMapping("/{id}/workspace")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER','BUH','DELO')")
    public CompanyWorkspaceDtos.Response workspace(@PathVariable Long id) { return service.get(id); }
}
