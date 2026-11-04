package com.adrotation.crm.documents.controller;

import com.adrotation.crm.documents.dto.SpecificationDtos;
import com.adrotation.crm.documents.entity.Contract;
import com.adrotation.crm.documents.service.ContractService;
import com.adrotation.crm.documents.service.LocalDocumentStorageService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ContractController {

    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final ContractService contractService;
    private final LocalDocumentStorageService storage;

    @PostMapping("/api/contracts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BUH','DELO')")
    public SpecificationDtos.ContractResponse create(
            @Valid @RequestBody SpecificationDtos.ContractRequest request
    ) {
        return contractService.create(request);
    }

    @GetMapping("/api/contracts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BUH','DELO')")
    public List<SpecificationDtos.ContractResponse> list(@RequestParam Long companyId) {
        return contractService.list(companyId);
    }

    /**
     * Backward-compatible endpoint used by the first frontend version.
     */
    @GetMapping("/api/companies/{companyId}/contracts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BUH','DELO')")
    public List<SpecificationDtos.ContractResponse> listByCompany(@PathVariable Long companyId) {
        return contractService.list(companyId);
    }

    @GetMapping("/api/contracts/{id}/download/{format}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','BUH','DELO')")
    public ResponseEntity<Resource> download(
            @PathVariable Long id,
            @PathVariable String format
    ) {
        Contract contract = contractService.require(id);

        String path;
        String extension;
        MediaType mediaType;

        if ("pdf".equalsIgnoreCase(format)) {
            path = contract.getPdfPath();
            extension = "pdf";
            mediaType = MediaType.APPLICATION_PDF;
        } else if ("docx".equalsIgnoreCase(format)) {
            path = contract.getDocxPath();
            extension = "docx";
            mediaType = DOCX_MEDIA_TYPE;
        } else {
            throw new BusinessException("Формат должен быть pdf или docx");
        }

        if (path == null || path.isBlank()) {
            throw new BusinessException(
                    "Файл договора в формате " + extension.toUpperCase() + " ещё не сохранён"
            );
        }

        Resource resource = storage.load(path);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("Договор-" + contract.getNumber() + "." + extension, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }
}
