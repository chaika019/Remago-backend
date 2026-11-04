package com.adrotation.crm.notification.controller;

import com.adrotation.crm.notification.dto.NotificationDto;
import com.adrotation.crm.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER','LAWYER','SHIFT_WORKER','BUH','DELO')")
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationDto> feed() {
        return notificationService.currentUserFeed();
    }

    @PostMapping("/{id}/read")
    public NotificationDto markRead(@PathVariable Long id) {
        return notificationService.markRead(id);
    }
}
