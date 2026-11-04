package com.adrotation.crm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.google-drive")
public record GoogleDriveProperties(
        boolean enabled,
        String serviceAccountJson,
        String rootFolderId
) {}
