package com.adrotation.crm.calendar.controller;

import com.adrotation.crm.calendar.dto.CalendarDtos;
import com.adrotation.crm.calendar.service.CalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {
    private final CalendarService service;

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CalendarDtos.Event create(@Valid @RequestBody CalendarDtos.SaveRequest request) {
        return service.create(request);
    }

    @PutMapping("/events/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CalendarDtos.Event update(@PathVariable Long id, @Valid @RequestBody CalendarDtos.SaveRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER','LAWYER','SHIFT_WORKER','BUH','DELO')")
    public CalendarDtos.Response calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.calendar(from, to);
    }
}
