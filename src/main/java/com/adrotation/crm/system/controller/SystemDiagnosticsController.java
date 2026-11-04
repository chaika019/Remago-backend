package com.adrotation.crm.system.controller;

import com.adrotation.crm.security.dto.SecurityDtos;
import com.adrotation.crm.system.service.SystemDiagnosticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemDiagnosticsController {
    private final SystemDiagnosticsService service;

    @GetMapping("/diagnostics")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SecurityDtos.DiagnosticsResponse diagnostics() { return service.check(); }
}
