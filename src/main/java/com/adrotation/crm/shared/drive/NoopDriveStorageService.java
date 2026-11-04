package com.adrotation.crm.shared.drive;

import com.adrotation.crm.shared.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public class NoopDriveStorageService implements DriveStorageService {
    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public UploadResult upload(String folderId, MultipartFile file) {
        throw new BusinessException("Google Drive интеграция отключена. Включите GOOGLE_DRIVE_ENABLED=true или используйте привязку существующего driveFileId.");
    }

    @Override
    public List<DriveFolder> listChildFolders(String parentFolderId) {
        return List.of();
    }
}
