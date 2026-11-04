package com.adrotation.crm.documents.service;

import com.adrotation.crm.shared.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GotenbergDocumentConverter {
    private final RestClient client;
    private final boolean enabled;

    public GotenbergDocumentConverter(RestClient.Builder builder,
                                      @Value("${app.documents.gotenberg-url:http://localhost:3001}") String baseUrl,
                                      @Value("${app.documents.pdf-enabled:true}") boolean enabled) {
        this.client = builder.baseUrl(baseUrl).build();
        this.enabled = enabled;
    }

    public byte[] docxToPdf(String fileName, byte[] docx) {
        if (!enabled) return null;
        try {
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("files", new ByteArrayResource(docx) {
                @Override public String getFilename() { return fileName; }
            }).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));

            return client.post()
                    .uri("/forms/libreoffice/convert")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception e) {
            throw new BusinessException("Не удалось преобразовать DOCX в PDF через Gotenberg: " + e.getMessage());
        }
    }
}
