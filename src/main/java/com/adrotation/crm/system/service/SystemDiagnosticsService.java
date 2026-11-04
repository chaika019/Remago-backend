package com.adrotation.crm.system.service;

import com.adrotation.crm.security.dto.SecurityDtos;
import com.adrotation.crm.shared.drive.DriveStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemDiagnosticsService {
    private final JdbcTemplate jdbcTemplate;
    private final RestClient.Builder restClientBuilder;
    private final DriveStorageService driveStorageService;

    @Value("${app.documents.storage-dir:./data/documents}") private String storageDir;
    @Value("${app.documents.gotenberg-url:http://localhost:3001}") private String gotenbergUrl;
    @Value("${spring.mail.username:}") private String mailUsername;

    public SecurityDtos.DiagnosticsResponse check() {
        List<SecurityDtos.DiagnosticCheck> checks = new ArrayList<>();
        checks.add(db());
        checks.add(storage());
        checks.add(gotenberg());
        checks.add(new SecurityDtos.DiagnosticCheck("Email SMTP", mailUsername == null || mailUsername.isBlank() ? "WARN" : "UP",
                mailUsername == null || mailUsername.isBlank() ? "MAIL_USERNAME не настроен" : "Отправитель настроен"));
        checks.add(new SecurityDtos.DiagnosticCheck("Google Drive", driveStorageService.isEnabled() ? "UP" : "WARN",
                driveStorageService.isEnabled() ? "Интеграция включена" : "Интеграция отключена"));
        return new SecurityDtos.DiagnosticsResponse(Instant.now(), "REMAGO FLOW backend", "1.0.0", checks);
    }

    private SecurityDtos.DiagnosticCheck db() {
        try {
            Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
            String version = jdbcTemplate.queryForObject("select coalesce(max(version), '0') from flyway_schema_history where success = true", String.class);
            return new SecurityDtos.DiagnosticCheck("PostgreSQL", one != null && one == 1 ? "UP" : "DOWN", "Flyway schema v" + version);
        } catch (Exception ex) {
            return new SecurityDtos.DiagnosticCheck("PostgreSQL", "DOWN", safe(ex));
        }
    }

    private SecurityDtos.DiagnosticCheck storage() {
        try {
            Path path = Path.of(storageDir).toAbsolutePath();
            Files.createDirectories(path);
            long usable = Files.getFileStore(path).getUsableSpace();
            return new SecurityDtos.DiagnosticCheck("Документы", Files.isWritable(path) ? "UP" : "DOWN",
                    path + " · свободно " + (usable / 1024 / 1024) + " МБ");
        } catch (Exception ex) {
            return new SecurityDtos.DiagnosticCheck("Документы", "DOWN", safe(ex));
        }
    }

    private SecurityDtos.DiagnosticCheck gotenberg() {
        try {
            String body = restClientBuilder.baseUrl(gotenbergUrl).build().get().uri("/health").retrieve().body(String.class);
            return new SecurityDtos.DiagnosticCheck("Gotenberg", "UP", body == null ? "PDF converter отвечает" : body);
        } catch (Exception ex) {
            return new SecurityDtos.DiagnosticCheck("Gotenberg", "DOWN", safe(ex));
        }
    }

    private String safe(Throwable ex) {
        String m = ex.getMessage();
        if (m == null) return ex.getClass().getSimpleName();
        return m.length() > 220 ? m.substring(0, 220) : m;
    }
}
