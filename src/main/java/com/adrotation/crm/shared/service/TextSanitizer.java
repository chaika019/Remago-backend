package com.adrotation.crm.shared.service;

import org.springframework.stereotype.Component;

@Component
public class TextSanitizer {
    public String plain(String value) {
        if (value == null) return null;
        String withoutTags = value.replaceAll("<[^>]*>", " ");
        String withoutControls = withoutTags.replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "");
        return withoutControls.replace('\u00A0', ' ').replaceAll("[ \\t]+", " ").trim();
    }
}
