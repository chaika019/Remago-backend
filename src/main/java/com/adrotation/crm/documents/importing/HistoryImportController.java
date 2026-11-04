package com.adrotation.crm.documents.importing;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import/history")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','BUH','DELO')")
public class HistoryImportController {
    private final HistoryImportService service;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HistoryImportDtos.PreviewResponse preview(@RequestPart("file") MultipartFile file) { return service.preview(file); }

    @PostMapping(value = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HistoryImportDtos.CommitResponse commit(@RequestPart("file") MultipartFile file) { return service.commit(file); }

    @PostMapping("/drive/preview")
    public HistoryImportDtos.PreviewResponse previewDrive(@Valid @RequestBody HistoryImportDtos.DriveSourceRequest r) { return service.previewDrive(r.urlOrFileId()); }

    @PostMapping("/drive/commit")
    public HistoryImportDtos.CommitResponse commitDrive(@Valid @RequestBody HistoryImportDtos.DriveSourceRequest r) { return service.commitDrive(r.urlOrFileId()); }
}
