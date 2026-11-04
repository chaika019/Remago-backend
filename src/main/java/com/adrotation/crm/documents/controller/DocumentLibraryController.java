package com.adrotation.crm.documents.controller;

import com.adrotation.crm.documents.dto.DocumentLibraryDtos;
import com.adrotation.crm.documents.entity.StoredDocument;
import com.adrotation.crm.documents.enums.DocumentLibraryType;
import com.adrotation.crm.documents.service.DocumentLibraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/document-library")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','BUH','DELO')")
public class DocumentLibraryController {

    private final DocumentLibraryService service;

    @GetMapping
    public DocumentLibraryDtos.PageResponse list(
            @RequestParam(required = false) DocumentLibraryType type,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return service.list(type, query, companyId, dateFrom, dateTo, page, size);
    }

    @GetMapping("/summary")
    public DocumentLibraryDtos.SummaryResponse summary() {
        return service.summary();
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentLibraryDtos.UploadResponse upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam DocumentLibraryType type,
            @RequestParam String title,
            @RequestParam(required = false) String number,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDate,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long dealId,
            @RequestParam(required = false) String notes
    ) {
        return service.upload(
                file,
                type,
                title,
                number,
                documentDate,
                companyId,
                dealId,
                notes
        );
    }

    @GetMapping("/uploaded/{id}/download")
    public ResponseEntity<Resource> downloadUploaded(@PathVariable Long id) {
        StoredDocument document = service.requireUploaded(id);
        Resource resource = service.loadUploaded(id);

        MediaType mediaType = parseMediaType(document.getMimeType());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.getOriginalFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    private MediaType parseMediaType(String value) {
        if (value == null || value.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        try {
            return MediaType.parseMediaType(value);
        } catch (IllegalArgumentException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
