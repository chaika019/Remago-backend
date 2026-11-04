package com.adrotation.crm.schedule.controller;

import com.adrotation.crm.schedule.dto.WeekDto;
import com.adrotation.crm.schedule.service.WeekService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/weeks")
@RequiredArgsConstructor
public class WeekController {
    private final WeekService weekService;

    @GetMapping
    public List<WeekDto> list(@RequestParam(defaultValue = "2026") int year) {
        return weekService.list(year);
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<WeekDto> sync(@RequestParam(defaultValue = "2026") int year) {
        return weekService.syncYear(year);
    }
}
