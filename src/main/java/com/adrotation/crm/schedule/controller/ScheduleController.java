package com.adrotation.crm.schedule.controller;

import com.adrotation.crm.schedule.dto.ScheduleDtos;
import com.adrotation.crm.city.enums.CityCategory;
import com.adrotation.crm.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {
    private final ScheduleService scheduleService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER','LAWYER','SHIFT_WORKER','BUH','DELO')")
    public ScheduleDtos.ScheduleResponse get(@RequestParam(defaultValue = "2026") int year,
                                              @RequestParam(defaultValue = "1") int weekFrom,
                                              @RequestParam(defaultValue = "53") int weekTo,
                                              @RequestParam(required = false) CityCategory category) {
        return scheduleService.get(year, weekFrom, weekTo, category);
    }
}
