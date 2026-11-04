package com.adrotation.crm.shared.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextSanitizerTest {

    private final TextSanitizer sanitizer = new TextSanitizer();

    @Test
    void removesMarkupAndNormalizesWhitespace() {
        assertEquals("ООО Ремаго Минск", sanitizer.plain("  ООО <b>Ремаго</b>\t Минск  "));
    }

    @Test
    void keepsNullAsNull() {
        assertNull(sanitizer.plain(null));
    }
}
