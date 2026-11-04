package com.adrotation.crm.shared.drive;

import com.adrotation.crm.shared.exception.BusinessException;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

@RequiredArgsConstructor
public class GoogleDriveStorageService implements DriveStorageService {
    private final Drive drive;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public UploadResult upload(String folderId, MultipartFile file) {
        if (folderId == null || folderId.isBlank()) {
            throw new BusinessException("Для недели не настроена папка Google Drive");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Файл фотоотчёта пуст");
        }
        try {
            File metadata = new File()
                    .setName(file.getOriginalFilename() == null ? "photo-report" : file.getOriginalFilename())
                    .setParents(List.of(folderId));
            InputStreamContent media = new InputStreamContent(
                    file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                    file.getInputStream());
            File uploaded = drive.files().create(metadata, media)
                    .setFields("id,webViewLink")
                    .execute();
            return new UploadResult(uploaded.getId(), uploaded.getWebViewLink());
        } catch (Exception ex) {
            throw new BusinessException("Ошибка загрузки фотоотчёта в Google Drive: " + ex.getMessage());
        }
    }

    @Override
    public List<DriveFolder> listChildFolders(String parentFolderId) {
        if (parentFolderId == null || parentFolderId.isBlank()) return List.of();
        try {
            String safeParent = parentFolderId.replace("'", "\\'");
            String query = "'" + safeParent + "' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false";
            return drive.files().list()
                    .setQ(query)
                    .setFields("files(id,name)")
                    .execute()
                    .getFiles()
                    .stream()
                    .map(f -> new DriveFolder(f.getId(), f.getName()))
                    .toList();
        } catch (Exception ex) {
            throw new BusinessException("Ошибка чтения папок Google Drive: " + ex.getMessage());
        }
    }

    @Override
    public DownloadResult download(String fileId) {
        if (fileId == null || fileId.isBlank()) throw new BusinessException("Не указан Google Drive fileId");
        try {
            File metadata = drive.files().get(fileId).setFields("id,name,mimeType,webViewLink").execute();
            String mime = metadata.getMimeType();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String name = metadata.getName();
            String resultMime = mime;
            if ("application/vnd.google-apps.spreadsheet".equals(mime)) {
                resultMime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                drive.files().export(fileId, resultMime).executeMediaAndDownloadTo(out);
                if (!name.toLowerCase().endsWith(".xlsx")) name += ".xlsx";
            } else if ("application/vnd.google-apps.document".equals(mime)) {
                resultMime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                drive.files().export(fileId, resultMime).executeMediaAndDownloadTo(out);
                if (!name.toLowerCase().endsWith(".docx")) name += ".docx";
            } else {
                drive.files().get(fileId).executeMediaAndDownloadTo(out);
            }
            return new DownloadResult(fileId, name, resultMime, out.toByteArray(), metadata.getWebViewLink());
        } catch (Exception ex) {
            throw new BusinessException("Не удалось скачать файл из Google Drive: " + ex.getMessage());
        }
    }
}
