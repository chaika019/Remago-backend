package com.adrotation.crm.documents.service;

import com.adrotation.crm.shared.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Service
public class LocalDocumentStorageService {

    private final Path root;

    public LocalDocumentStorageService(
            @Value("${app.documents.storage-dir:./data/documents}") String storageDir
    ) {
        try {
            this.root = Paths.get(storageDir).toAbsolutePath().normalize();
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось создать каталог документов", e);
        }
    }

    public String save(String fileName, byte[] data) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Имя файла не может быть пустым");
        }
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Нельзя сохранить пустой документ");
        }

        try {
            Path target = safePath(fileName);
            Files.write(
                    target,
                    data,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            return target.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сохранить документ " + fileName, e);
        }
    }

    public Resource load(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new NotFoundException("Файл документа не найден");
        }

        try {
            Path path = Paths.get(storedPath).toAbsolutePath().normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw new NotFoundException("Файл документа не найден");
            }

            return new UrlResource(path.toUri());
        } catch (java.net.MalformedURLException e) {
            throw new NotFoundException("Файл документа не найден");
        }
    }

    public void delete(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }

        try {
            Path path = Paths.get(storedPath).toAbsolutePath().normalize();
            if (path.startsWith(root)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Cleanup is best-effort. The original business error must not be hidden.
        }
    }

    private Path safePath(String fileName) {
        Path name = Path.of(fileName).getFileName();
        if (name == null || name.toString().isBlank()) {
            throw new IllegalArgumentException("Некорректное имя файла");
        }

        Path target = root.resolve(name.toString()).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Некорректное имя файла");
        }

        return target;
    }
}
