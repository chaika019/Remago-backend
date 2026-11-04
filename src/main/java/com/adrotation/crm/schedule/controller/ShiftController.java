package com.adrotation.crm.schedule.controller;

import com.adrotation.crm.schedule.dto.ShiftDtos;
import com.adrotation.crm.schedule.service.ShiftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shifts")
@RequiredArgsConstructor
public class ShiftController {
    private final ShiftService shiftService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER','LAWYER','SHIFT_WORKER','BUH','DELO')")
    public ShiftDtos.ShiftResponse get(@PathVariable Long id) {
        return shiftService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ShiftDtos.ShiftResponse create(@Valid @RequestBody ShiftDtos.CreateShiftRequest request) {
        return shiftService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ShiftDtos.ShiftResponse update(@PathVariable Long id, @RequestBody ShiftDtos.UpdateShiftRequest request) {
        return shiftService.update(id, request);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ShiftDtos.ShiftResponse complete(@PathVariable Long id,
                                             @RequestParam(defaultValue = "false") boolean withIssues) {
        return shiftService.markCompletedByShiftWorker(id, withIssues);
    }

    @PostMapping("/{id}/proposal")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void propose(@PathVariable Long id, @Valid @RequestBody ShiftDtos.ShiftProposalRequest request) {
        shiftService.proposeStatus(id, request);
    }

    @GetMapping("/statuses")
    public List<ShiftDtos.ShiftStatusMeta> statuses() {
        return shiftService.statusMeta();
    }
}
