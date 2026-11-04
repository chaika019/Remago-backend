package com.adrotation.crm.documents.controller;

import com.adrotation.crm.documents.dto.SpecificationDtos;
import com.adrotation.crm.documents.entity.Specification;
import com.adrotation.crm.documents.service.DocumentDeliveryService;
import com.adrotation.crm.documents.service.LocalDocumentStorageService;
import com.adrotation.crm.documents.service.SpecificationService;
import com.adrotation.crm.shared.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class SpecificationController {

    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final SpecificationService specificationService;
    private final DocumentDeliveryService deliveryService;
    private final LocalDocumentStorageService storage;

    @GetMapping("/api/deals/{dealId}/document-offer")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BUH','DELO')")
    public SpecificationDtos.DocumentOfferResponse offer(@PathVariable Long dealId) {
        return specificationService.offer(dealId);
    }

    @PostMapping("/api/deals/{dealId}/documents/generate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BUH','DELO')")
    public SpecificationDtos.GenerateDocumentsResponse generate(@PathVariable Long dealId) {
        return specificationService.generate(dealId);
    }

    @GetMapping("/api/deals/{dealId}/specifications")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BUH','DELO')")
    public List<SpecificationDtos.SpecificationResponse> list(@PathVariable Long dealId) {
        return specificationService.listForDeal(dealId);
    }

    @PostMapping("/api/specifications")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BUH','DELO')")
    public SpecificationDtos.SpecificationResponse createManual(
            @Valid @RequestBody SpecificationDtos.ManualSpecificationRequest request
    ) {
        return specificationService.createManual(request);
    }

    @PostMapping("/api/specifications/{id}/clone")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BUH','DELO')")
    public SpecificationDtos.SpecificationResponse cloneSpecification(
            @PathVariable Long id,
            @RequestBody(required = false) SpecificationDtos.CloneSpecificationRequest request
    ) {
        return specificationService.cloneSpecification(id, request);
    }

    @PostMapping("/api/specifications/{id}/deliver")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BUH','DELO')")
    public SpecificationDtos.DeliveryResponse deliver(
            @PathVariable Long id,
            @Valid @RequestBody SpecificationDtos.DeliveryRequest request
    ) {
        return deliveryService.deliver(id, request);
    }

    @GetMapping("/api/specifications/{id}/download/{format}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BUH','DELO')")
    public ResponseEntity<Resource> download(
            @PathVariable Long id,
            @PathVariable String format
    ) {
        Specification specification = specificationService.require(id);

        String path;
        String extension;
        MediaType mediaType;

        if ("pdf".equalsIgnoreCase(format)) {
            path = specification.getPdfPath();
            extension = "pdf";
            mediaType = MediaType.APPLICATION_PDF;
        } else if ("docx".equalsIgnoreCase(format)) {
            path = specification.getDocxPath();
            extension = "docx";
            mediaType = DOCX_MEDIA_TYPE;
        } else {
            throw new BusinessException("Формат должен быть pdf или docx");
        }

        if (path == null || path.isBlank()) {
            throw new BusinessException("Этот формат спецификации ещё не сформирован");
        }

        Resource resource = storage.load(path);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("Спецификация-" + specification.getNumber() + "." + extension, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }
}
