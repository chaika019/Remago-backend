package com.adrotation.crm.company.service;

import com.adrotation.crm.shared.service.TextSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyNormalizationServiceTest {

    @Test
    void similarityDetectsCloseNames() {
        CompanyNormalizationService service = new CompanyNormalizationService(null, new TextSanitizer());
        double score = service.similarity("агролинк", "агролинк");
        assertTrue(score > 0.99);
    }

    @Test
    void similarityDetectsSmallTypo() {
        CompanyNormalizationService service = new CompanyNormalizationService(null, new TextSanitizer());
        double score = service.similarity("серволюкс", "сервалюкс");
        assertTrue(score > 0.85);
    }
}
