package com.adrotation.crm.dashboard.controller;

import com.adrotation.crm.dashboard.dto.DashboardSummaryDto;
import com.adrotation.crm.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER','LAWYER','SHIFT_WORKER','BUH','DELO')")
    public DashboardSummaryDto summary(@RequestParam(defaultValue = "2026") int year,
                                        @RequestParam(defaultValue = "1") int weekFrom,
                                        @RequestParam(defaultValue = "53") int weekTo) {
        return dashboardService.summary(year, weekFrom, weekTo);
    }
}
