package com.adrotation.crm.reports.controller;

import com.adrotation.crm.reports.dto.PhotoReportDto;
import com.adrotation.crm.reports.service.PhotoReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PhotoReportController {
    private final PhotoReportService photoReportService;

    @PostMapping(value = "/shifts/{shiftId}/photo-report", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ROP','ROK','MANAGER','SHIFT_WORKER')")
    public PhotoReportDto upload(@PathVariable Long shiftId, @RequestPart("file") MultipartFile file) {
        return photoReportService.upload(shiftId, file);
    }

    @PostMapping("/shifts/{shiftId}/photo-report/link")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ROP','ROK','MANAGER','SHIFT_WORKER')")
    public PhotoReportDto registerExisting(@PathVariable Long shiftId, @RequestBody ExistingDriveFile body) {
        return photoReportService.registerExisting(shiftId, body.driveFileId(), body.webViewLink());
    }

    @GetMapping("/shifts/{shiftId}/photo-reports")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DIRECTOR','ROP','ROK','MANAGER','LAWYER','SHIFT_WORKER')")
    public List<PhotoReportDto> list(@PathVariable Long shiftId) {
        return photoReportService.list(shiftId);
    }

    @PostMapping("/photo-reports/{id}/sent-to-client")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ROP','ROK','MANAGER')")
    public PhotoReportDto markSent(@PathVariable Long id) {
        return photoReportService.markSentToClient(id);
    }

    public record ExistingDriveFile(String driveFileId, String webViewLink) {}
}
