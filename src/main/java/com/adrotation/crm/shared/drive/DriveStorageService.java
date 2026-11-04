package com.adrotation.crm.shared.drive;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DriveStorageService {
    boolean isEnabled();
    UploadResult upload(String folderId, MultipartFile file);
    List<DriveFolder> listChildFolders(String parentFolderId);

    default DownloadResult download(String fileId) {
        throw new UnsupportedOperationException("Google Drive download is not configured");
    }

    record UploadResult(String fileId, String webViewLink) {}
    record DriveFolder(String id, String name) {}
    record DownloadResult(String fileId, String fileName, String mimeType, byte[] bytes, String webViewLink) {}
}
