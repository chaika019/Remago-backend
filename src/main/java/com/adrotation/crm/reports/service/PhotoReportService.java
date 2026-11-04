package com.adrotation.crm.reports.service;

import com.adrotation.crm.reports.dto.PhotoReportDto;
import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.reports.entity.PhotoReport;
import com.adrotation.crm.schedule.entity.Shift;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.shared.exception.NotFoundException;
import com.adrotation.crm.reports.repository.PhotoReportRepository;
import com.adrotation.crm.auth.security.CurrentUserService;
import com.adrotation.crm.schedule.service.ShiftService;
import com.adrotation.crm.shared.service.DtoMapper;
import com.adrotation.crm.shared.drive.DriveStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PhotoReportService {
    private final ShiftService shiftService;
    private final PhotoReportRepository photoReportRepository;
    private final CurrentUserService currentUserService;
    private final DriveStorageService driveStorageService;
    private final DtoMapper mapper;

    @Transactional
    public PhotoReportDto upload(Long shiftId, MultipartFile file) {
        Shift shift = shiftService.require(shiftId);
        String folderId = shift.getWeek().getDriveFolderId();
        DriveStorageService.UploadResult uploaded = driveStorageService.upload(folderId, file);
        return save(shift, uploaded.fileId(), uploaded.webViewLink());
    }

    @Transactional
    public PhotoReportDto registerExisting(Long shiftId, String driveFileId, String webViewLink) {
        if (driveFileId == null || driveFileId.isBlank()) throw new BusinessException("driveFileId обязателен");
        Shift shift = shiftService.require(shiftId);
        return save(shift, driveFileId.trim(), webViewLink == null || webViewLink.isBlank() ? "https://drive.google.com/file/d/" + driveFileId + "/view" : webViewLink.trim());
    }

    @Transactional(readOnly = true)
    public List<PhotoReportDto> list(Long shiftId) {
        shiftService.require(shiftId);
        return photoReportRepository.findByShift_IdOrderByCreatedAtDesc(shiftId).stream().map(mapper::photo).toList();
    }

    @Transactional
    public PhotoReportDto markSentToClient(Long id) {
        PhotoReport report = photoReportRepository.findById(id).orElseThrow(() -> new NotFoundException("Фотоотчёт не найден: " + id));
        report.setSentToClientAt(Instant.now());
        return mapper.photo(photoReportRepository.save(report));
    }

    private PhotoReportDto save(Shift shift, String fileId, String webViewLink) {
        AppUser user = currentUserService.requireCurrentUser();
        PhotoReport report = new PhotoReport();
        report.setShift(shift);
        report.setDriveFileId(fileId);
        report.setDriveWebViewLink(webViewLink);
        report.setUploadedBy(user);
        return mapper.photo(photoReportRepository.save(report));
    }
}
