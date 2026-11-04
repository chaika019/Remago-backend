package com.adrotation.crm.schedule.service;

import com.adrotation.crm.audit.service.AuditService;
import com.adrotation.crm.auth.security.CurrentUserService;
import com.adrotation.crm.city.entity.City;
import com.adrotation.crm.city.service.CityService;
import com.adrotation.crm.company.service.CompanyService;
import com.adrotation.crm.notification.enums.NotificationTriggerType;
import com.adrotation.crm.notification.service.NotificationService;
import com.adrotation.crm.schedule.dto.ShiftDtos;
import com.adrotation.crm.schedule.entity.Shift;
import com.adrotation.crm.schedule.entity.WeekPeriod;
import com.adrotation.crm.schedule.enums.ShiftStatus;
import com.adrotation.crm.schedule.repository.ShiftRepository;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.shared.exception.NotFoundException;
import com.adrotation.crm.shared.service.DtoMapper;
import com.adrotation.crm.shared.service.TextSanitizer;
import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.user.enums.Role;
import com.adrotation.crm.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final CityService cityService;
    private final WeekService weekService;
    private final CompanyService companyService;
    private final AppUserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final TextSanitizer sanitizer;
    private final DtoMapper mapper;

    public Shift require(Long id) {
        return shiftRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Смена не найдена: " + id));
    }

    @Transactional(readOnly = true)
    public ShiftDtos.ShiftResponse get(Long id) {
        return mapper.shift(require(id));
    }

    @Transactional
    public ShiftDtos.ShiftResponse create(ShiftDtos.CreateShiftRequest request) {
        City city = cityService.requireForUpdate(request.cityId());
        if (!city.isRotationEnabled()) {
            throw new BusinessException("Сначала включите город в график физической ротации в справочнике городов");
        }
        WeekPeriod week = weekService.require(request.weekId());

        if (shiftRepository.existsByCity_IdAndWeek_Id(city.getId(), week.getId())) {
            throw new BusinessException(
                    "Для города «" + city.getName() + "» на неделю " + week.getIsoWeekNumber() + " смена уже существует"
            );
        }

        Shift shift = new Shift();
        shift.setCity(city);
        shift.setWeek(week);
        shift.setStatus(city.isNeedsRepair()
                ? ShiftStatus.REPAIR_AND_RECONSTRUCTION
                : defaultStatus(request.status()));
        shift.setCompany(request.companyId() == null ? null : companyService.require(request.companyId()));
        shift.setResponsiblePerson(resolveUser(request.responsiblePersonId()));
        shift.setA4Sold(request.a4Sold());
        shift.setFailureReason(sanitizer.plain(request.failureReason()));

        validateStatus(shift);

        Shift saved = shiftRepository.save(shift);
        auditService.record("SHIFT", saved.getId(), "created", null, saved.getStatus());
        applyStatusSideEffects(saved, saved.getStatus());
        return mapper.shift(saved);
    }

    @Transactional
    public ShiftDtos.ShiftResponse update(Long id, ShiftDtos.UpdateShiftRequest request) {
        Shift shift = require(id);
        ShiftStatus oldStatus = shift.getStatus();

        if (Boolean.TRUE.equals(request.clearCompany())) {
            auditService.record("SHIFT", id, "companyId", shift.getCompany() == null ? null : shift.getCompany().getId(), null);
            shift.setCompany(null);
        } else if (request.companyId() != null) {
            Long oldCompanyId = shift.getCompany() == null ? null : shift.getCompany().getId();
            auditService.record("SHIFT", id, "companyId", oldCompanyId, request.companyId());
            shift.setCompany(companyService.require(request.companyId()));
        }

        if (Boolean.TRUE.equals(request.clearResponsiblePerson())) {
            auditService.record("SHIFT", id, "responsiblePersonId", shift.getResponsiblePerson() == null ? null : shift.getResponsiblePerson().getId(), null);
            shift.setResponsiblePerson(null);
        } else if (request.responsiblePersonId() != null) {
            Long oldResponsibleId = shift.getResponsiblePerson() == null ? null : shift.getResponsiblePerson().getId();
            auditService.record("SHIFT", id, "responsiblePersonId", oldResponsibleId, request.responsiblePersonId());
            shift.setResponsiblePerson(resolveUser(request.responsiblePersonId()));
        }

        if (request.a4Sold() != null) {
            auditService.record("SHIFT", id, "a4Sold", shift.isA4Sold(), request.a4Sold());
            shift.setA4Sold(request.a4Sold());
        }

        if (request.failureReason() != null) {
            String clean = sanitizer.plain(request.failureReason());
            auditService.record("SHIFT", id, "failureReason", shift.getFailureReason(), clean);
            shift.setFailureReason(clean);
        }

        if (request.status() != null) {
            ShiftStatus requestedStatus = shift.getCity().isNeedsRepair()
                    ? ShiftStatus.REPAIR_AND_RECONSTRUCTION
                    : request.status();
            auditService.record("SHIFT", id, "status", oldStatus, requestedStatus);
            shift.setStatus(requestedStatus);
        }

        validateStatus(shift);
        Shift saved = shiftRepository.save(shift);

        if (!Objects.equals(oldStatus, saved.getStatus())) {
            applyStatusSideEffects(saved, saved.getStatus());
        }

        return mapper.shift(saved);
    }

    @Transactional
    public ShiftDtos.ShiftResponse markCompletedByShiftWorker(Long id, boolean withIssues) {
        AppUser current = currentUserService.requireCurrentUser();
        if (!current.getRoles().contains(Role.SHIFT_WORKER) && !hasShiftManagementRole(current)) {
            throw new AccessDeniedException("Операция доступна сменщику или руководителю ротации");
        }

        Shift shift = require(id);
        if (shift.getCity().isNeedsRepair()) {
            throw new BusinessException("Город находится в ремонте; завершение рекламной смены недоступно");
        }

        ShiftStatus oldStatus = shift.getStatus();
        ShiftStatus newStatus = withIssues
                ? ShiftStatus.PLUS_MINUS
                : ShiftStatus.SHIFT_AND_NOTIFICATION;

        shift.setStatus(newStatus);
        auditService.record("SHIFT", id, "status", oldStatus, newStatus);

        Shift saved = shiftRepository.save(shift);
        applyStatusSideEffects(saved, newStatus);
        return mapper.shift(saved);
    }

    @Transactional
    public void proposeStatus(Long id, ShiftDtos.ShiftProposalRequest request) {
        AppUser current = currentUserService.requireCurrentUser();
        if (!current.getRoles().contains(Role.MANAGER) && !current.getRoles().contains(Role.SUPER_ADMIN)) {
            throw new AccessDeniedException("Предложить изменение статуса может менеджер");
        }

        Shift shift = require(id);
        if (shift.getCity().isNeedsRepair()) {
            throw new BusinessException("Для города в ремонте статус управляется состоянием ремонта");
        }

        String comment = sanitizer.plain(request.comment());
        String payload = "Менеджер " + current.getFullName()
                + " предлагает статус «" + request.desiredStatus().getDisplayName() + "» для города "
                + shift.getCity().getName()
                + (comment == null || comment.isBlank() ? "" : ". Комментарий: " + comment);

        auditService.record("SHIFT", id, "statusProposal", null, payload);
        notificationService.notifyRoleAlways(
                NotificationTriggerType.GENERIC,
                "SHIFT",
                id,
                Role.ROP,
                "Предложение изменения смены",
                payload
        );
        notificationService.notifyRoleAlways(
                NotificationTriggerType.GENERIC,
                "SHIFT",
                id,
                Role.ROK,
                "Предложение изменения смены",
                payload
        );
        notificationService.notifyRoleAlways(
                NotificationTriggerType.GENERIC,
                "SHIFT",
                id,
                Role.SUPER_ADMIN,
                "Предложение изменения смены",
                payload
        );
    }

    public List<ShiftDtos.ShiftStatusMeta> statusMeta() {
        return Arrays.stream(ShiftStatus.values())
                .map(status -> new ShiftDtos.ShiftStatusMeta(
                        status,
                        status.getLegendNumber(),
                        status.getDisplayName(),
                        status.getColorHex()
                ))
                .toList();
    }

    private ShiftStatus defaultStatus(ShiftStatus status) {
        return status == null ? ShiftStatus.PLANNED : status;
    }

    private AppUser resolveUser(Long id) {
        if (id == null) {
            return null;
        }
        return userRepository.findById(id)
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new NotFoundException("Активный пользователь не найден: " + id));
    }

    private void validateStatus(Shift shift) {
        if (shift.getStatus() == null) {
            throw new BusinessException("Статус смены обязателен");
        }

        if (shift.getStatus() == ShiftStatus.WHITE_SHIFT && !shift.isA4Sold()) {
            throw new BusinessException(
                    "Внеплановая белая смена считается состоявшейся только при проданном формате A4. Установите a4Sold=true."
            );
        }

        if (shift.getStatus() == ShiftStatus.NOT_OCCURRED
                && (shift.getFailureReason() == null || shift.getFailureReason().isBlank())) {
            throw new BusinessException("Для статуса «Не состоялась» укажите причину срыва");
        }
    }

    private void applyStatusSideEffects(Shift shift, ShiftStatus newStatus) {
        if (newStatus == ShiftStatus.WHITE_SHIFT) {
            notificationService.notifyRole(
                    NotificationTriggerType.WHITE_SHIFT_CREATED,
                    "SHIFT",
                    shift.getId(),
                    Role.ROP,
                    "Внеплановая смена",
                    "В график добавлена внеплановая смена: " + shift.getCity().getName()
            );
            notificationService.notifyRole(
                    NotificationTriggerType.WHITE_SHIFT_CREATED,
                    "SHIFT",
                    shift.getId(),
                    Role.ROK,
                    "Внеплановая смена",
                    "В график добавлена внеплановая смена: " + shift.getCity().getName()
            );
            notificationService.notifyRole(
                    NotificationTriggerType.WHITE_SHIFT_CREATED,
                    "SHIFT",
                    shift.getId(),
                    Role.SUPER_ADMIN,
                    "Внеплановая смена",
                    "В график добавлена внеплановая смена: " + shift.getCity().getName()
            );

            if (shift.getResponsiblePerson() != null) {
                notificationService.notifyUser(
                        NotificationTriggerType.WHITE_SHIFT_CREATED,
                        "SHIFT",
                        shift.getId(),
                        shift.getResponsiblePerson(),
                        "Внеплановая смена",
                        "В график добавлена внеплановая смена: " + shift.getCity().getName()
                );
            }

            createNextShiftAfter28Days(shift);
        }

        if (newStatus == ShiftStatus.NOT_OCCURRED || newStatus == ShiftStatus.MINUS) {
            NotificationTriggerType trigger = newStatus == ShiftStatus.MINUS
                    ? NotificationTriggerType.SHIFT_MINUS
                    : NotificationTriggerType.SHIFT_NOT_OCCURRED;
            String title = newStatus == ShiftStatus.MINUS
                    ? "Срочно найти сменщика!"
                    : "Смена не состоялась";
            String payload = "Город: " + shift.getCity().getName()
                    + ", неделя: " + shift.getWeek().getIsoWeekNumber()
                    + (shift.getFailureReason() == null ? "" : ", причина: " + shift.getFailureReason());

            notificationService.notifyRolesAlways(
                    trigger,
                    "SHIFT",
                    shift.getId(),
                    List.of(Role.DIRECTOR, Role.LAWYER, Role.ROP, Role.ROK, Role.SUPER_ADMIN),
                    title,
                    payload
            );

            if (shift.getResponsiblePerson() != null) {
                notificationService.notifyUserAlways(
                        trigger,
                        "SHIFT",
                        shift.getId(),
                        shift.getResponsiblePerson(),
                        title,
                        payload
                );
            }
        }

        if (newStatus == ShiftStatus.SHIFT_AND_NOTIFICATION && shift.getResponsiblePerson() != null) {
            notificationService.notifyUser(
                    NotificationTriggerType.PHOTO_REPORT_REQUIRED,
                    "SHIFT",
                    shift.getId(),
                    shift.getResponsiblePerson(),
                    "Отправьте фотоотчёт клиенту",
                    "Смена в городе " + shift.getCity().getName()
                            + " состоялась. Загрузите и отправьте клиенту фотоотчёт."
            );
        }
    }

    private boolean hasShiftManagementRole(AppUser user) {
        return user.getRoles().contains(Role.SUPER_ADMIN)
                || user.getRoles().contains(Role.ROP)
                || user.getRoles().contains(Role.ROK);
    }

    private void createNextShiftAfter28Days(Shift source) {
        if (source.getCity().isNeedsRepair() || !source.getCity().isRotationEnabled()) {
            return;
        }

        LocalDate nextDate = source.getWeek().getStartDate().plusDays(28);
        WeekPeriod nextWeek = weekService.requireByDate(nextDate);

        if (shiftRepository.existsByCity_IdAndWeek_Id(source.getCity().getId(), nextWeek.getId())) {
            return;
        }

        Shift next = new Shift();
        next.setCity(source.getCity());
        next.setWeek(nextWeek);
        next.setStatus(ShiftStatus.PLANNED);
        next.setResponsiblePerson(source.getResponsiblePerson());
        next.setAutoGeneratedFromShift(source);
        Shift saved = shiftRepository.save(next);
        auditService.record("SHIFT", saved.getId(), "autoGeneratedFromShiftId", null, source.getId());
    }
}
