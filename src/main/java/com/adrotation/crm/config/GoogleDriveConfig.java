package com.adrotation.crm.config;

import com.adrotation.crm.shared.drive.DriveStorageService;
import com.adrotation.crm.shared.drive.GoogleDriveStorageService;
import com.adrotation.crm.shared.drive.NoopDriveStorageService;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Configuration
@EnableConfigurationProperties(GoogleDriveProperties.class)
public class GoogleDriveConfig {

    @Bean
    @ConditionalOnProperty(name = "app.google-drive.enabled", havingValue = "true")
    Drive googleDrive(GoogleDriveProperties properties) throws Exception {
        String serviceAccountJson = properties.serviceAccountJson();
        if (serviceAccountJson == null || serviceAccountJson.isBlank()) {
            throw new IllegalStateException(
                    "GOOGLE_SERVICE_ACCOUNT_JSON is required when Google Drive integration is enabled"
            );
        }

        try (InputStream in = serviceAccountStream(serviceAccountJson)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                    .createScoped(List.of(DriveScopes.DRIVE));

            return new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials)
            )
                    .setApplicationName("REMAGO FLOW")
                    .build();
        }
    }

    @Bean
    @ConditionalOnBean(Drive.class)
    DriveStorageService googleDriveStorageService(Drive drive) {
        return new GoogleDriveStorageService(drive);
    }

    @Bean
    @ConditionalOnMissingBean(DriveStorageService.class)
    DriveStorageService noopDriveStorageService() {
        return new NoopDriveStorageService();
    }

    private InputStream serviceAccountStream(String configuredValue) throws IOException {
        String trimmed = configuredValue.trim();

        if (trimmed.startsWith("{")) {
            return new ByteArrayInputStream(trimmed.getBytes(StandardCharsets.UTF_8));
        }

        Path path = Path.of(trimmed).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(
                    "Google service-account JSON file was not found: " + path
            );
        }

        return Files.newInputStream(path);
    }
}
