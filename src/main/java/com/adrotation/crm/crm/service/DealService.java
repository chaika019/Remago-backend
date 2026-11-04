package com.adrotation.crm.crm.service;

import com.adrotation.crm.audit.service.AuditService;
import com.adrotation.crm.auth.security.CurrentUserService;
import com.adrotation.crm.city.entity.City;
import com.adrotation.crm.city.repository.CityRepository;
import com.adrotation.crm.company.service.CompanyService;
import com.adrotation.crm.crm.dto.DealDtos;
import com.adrotation.crm.crm.entity.Deal;
import com.adrotation.crm.crm.entity.DealPlacement;
import com.adrotation.crm.crm.enums.CalledToday;
import com.adrotation.crm.crm.enums.DocumentIntent;
import com.adrotation.crm.crm.enums.KanbanStage;
import com.adrotation.crm.crm.repository.DealRepository;
import com.adrotation.crm.schedule.entity.WeekPeriod;
import com.adrotation.crm.schedule.service.WeekService;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.shared.exception.NotFoundException;
import com.adrotation.crm.shared.service.DtoMapper;
import com.adrotation.crm.shared.service.TextSanitizer;
import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.user.enums.Role;
import com.adrotation.crm.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DealService {

    private final DealRepository dealRepository;
    private final CityRepository cityRepository;
    private final AppUserRepository userRepository;
    private final CompanyService companyService;
    private final GoalOptionService goalOptionService;
    private final WeekService weekService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final TextSanitizer sanitizer;
    private final DtoMapper mapper;
    private final PlacementPolicy placementPolicy;

    @Value("${app.time-zone:Europe/Minsk}")
    private String timeZone;

    @Transactional(readOnly = true)
    public List<DealDtos.DealResponse> list(int year, int weekNumber, Long managerId) {
        AppUser current = currentUserService.requireCurrentUser();
        WeekPeriod week = weekService.findByIsoWeek(year, weekNumber).orElse(null);
        if (week == null) return List.of();

        Long effectiveManager = managerId;
        if (isManagerOnly(current)) {
            effectiveManager = current.getId();
        }

        return dealRepository.findForWeek(week.getId(), effectiveManager)
                .stream()
                .map(mapper::deal)
                .toList();
    }

    @Transactional(readOnly = true)
    public DealDtos.DealResponse get(Long id) {
        Deal deal = require(id);
        AppUser current = currentUserService.requireCurrentUser();

        if (isManagerOnly(current) && !Objects.equals(deal.getCreatedBy().getId(), current.getId())) {
            throw new AccessDeniedException("Менеджер может просматривать только свои CRM-карточки");
        }

        return mapper.deal(deal);
    }

    @Transactional
    public DealDtos.DealResponse create(DealDtos.CreateDealRequest request) {
        AppUser current = currentUserService.requireCurrentUser();
        AppUser manager = resolveManagerForCreate(current, request.managerId());
        LocalDate activityDate = request.activityDate() == null
                ? LocalDate.now(ZoneId.of(timeZone))
                : request.activityDate();

        int dailySequence = nextDailySequence(manager.getId(), activityDate);

        Deal deal = new Deal();
        deal.setCompany(companyService.require(request.companyId()));
        deal.setTargetCities(loadCities(request.targetCityIds()));
        deal.setServiceType(request.serviceType());
        deal.setDailyGoal(request.dailyGoalId() == null ? null : goalOptionService.require(request.dailyGoalId()));
        deal.setGoalAchieved(request.goalAchieved());
        deal.setCalledToday(request.calledToday() == null ? CalledToday.NOT_CALLED : request.calledToday());
        deal.setDocumentIntent(request.documentIntent() == null ? DocumentIntent.NONE : request.documentIntent());
        deal.setNextCallDate(request.nextCallDate());
        deal.setNextAction(sanitizer.plain(request.nextAction()));
        deal.setNextActionDueDate(request.nextActionDueDate());
        deal.setNextActionCompletedAt(null);
        deal.setComment(sanitizer.plain(request.comment()));
        deal.setProspective(request.prospective());
        deal.setKanbanStage(resolveStage(request.goalAchieved(), request.prospective()));
        deal.setActivityDate(activityDate);
        deal.setWeek(weekService.requireByDate(activityDate));
        deal.setCreatedBy(manager);
        deal.setDailySequence(dailySequence);

        Deal saved = dealRepository.save(deal);
        if (request.placements() != null) {
            replacePlacements(saved, request.placements());
        }

        auditService.record("DEAL", saved.getId(), "created", null, "created");
        return mapper.deal(saved);
    }

    @Transactional
    public DealDtos.DealResponse update(Long id, DealDtos.UpdateDealRequest request) {
        Deal deal = require(id);
        assertCanEdit(deal);

        if (request.companyId() != null) {
            auditService.record("DEAL", id, "companyId", deal.getCompany().getId(), request.companyId());
            deal.setCompany(companyService.require(request.companyId()));
        }

        if (request.targetCityIds() != null) {
            Set<Long> oldIds = deal.getTargetCities().stream()
                    .map(City::getId)
                    .collect(java.util.stream.Collectors.toSet());
            auditService.record("DEAL", id, "targetCityIds", oldIds, request.targetCityIds());
            deal.setTargetCities(loadCities(request.targetCityIds()));
        }

        if (request.serviceType() != null) {
            auditService.record("DEAL", id, "serviceType", deal.getServiceType(), request.serviceType());
            deal.setServiceType(request.serviceType());
        }

        if (request.dailyGoalId() != null) {
            Long oldGoalId = deal.getDailyGoal() == null ? null : deal.getDailyGoal().getId();
            auditService.record("DEAL", id, "dailyGoalId", oldGoalId, request.dailyGoalId());
            deal.setDailyGoal(goalOptionService.require(request.dailyGoalId()));
        }

        if (request.goalAchieved() != null) {
            auditService.record("DEAL", id, "goalAchieved", deal.isGoalAchieved(), request.goalAchieved());
            deal.setGoalAchieved(request.goalAchieved());
        }

        if (request.calledToday() != null) {
            auditService.record("DEAL", id, "calledToday", deal.getCalledToday(), request.calledToday());
            deal.setCalledToday(request.calledToday());
        }

        if (request.documentIntent() != null) {
            auditService.record("DEAL", id, "documentIntent", deal.getDocumentIntent(), request.documentIntent());
            deal.setDocumentIntent(request.documentIntent());
        }

        if (request.placements() != null) {
            auditService.record("DEAL", id, "placements", deal.getPlacements().size(), request.placements().size());
            replacePlacements(deal, request.placements());
        }

        if (Boolean.TRUE.equals(request.clearNextCallDate())) {
            auditService.record("DEAL", id, "nextCallDate", deal.getNextCallDate(), null);
            deal.setNextCallDate(null);
        } else if (request.nextCallDate() != null) {
            auditService.record("DEAL", id, "nextCallDate", deal.getNextCallDate(), request.nextCallDate());
            deal.setNextCallDate(request.nextCallDate());
        }

        if (Boolean.TRUE.equals(request.clearNextAction())) {
            auditService.record("DEAL", id, "nextAction", deal.getNextAction(), null);
            deal.setNextAction(null);
            deal.setNextActionDueDate(null);
            deal.setNextActionCompletedAt(null);
        } else {
            if (request.nextAction() != null) {
                String cleanAction = sanitizer.plain(request.nextAction());
                auditService.record("DEAL", id, "nextAction", deal.getNextAction(), cleanAction);
                deal.setNextAction(cleanAction);
                if (deal.getNextActionCompletedAt() != null) deal.setNextActionCompletedAt(null);
            }
            if (request.nextActionDueDate() != null) {
                auditService.record("DEAL", id, "nextActionDueDate", deal.getNextActionDueDate(), request.nextActionDueDate());
                deal.setNextActionDueDate(request.nextActionDueDate());
            }
            if (Boolean.TRUE.equals(request.completeNextAction())) {
                if (deal.getNextAction() == null || deal.getNextAction().isBlank()) {
                    throw new BusinessException("Сначала укажите следующее действие");
                }
                auditService.record("DEAL", id, "nextActionCompletedAt", deal.getNextActionCompletedAt(), "completed");
                deal.setNextActionCompletedAt(java.time.Instant.now());
            }
        }

        if (request.comment() != null) {
            String clean = sanitizer.plain(request.comment());
            auditService.record("DEAL", id, "comment", deal.getComment(), clean);
            deal.setComment(clean);
        }

        if (request.prospective() != null) {
            auditService.record("DEAL", id, "prospective", deal.isProspective(), request.prospective());
            deal.setProspective(request.prospective());
        }

        if (request.activityDate() != null && !request.activityDate().equals(deal.getActivityDate())) {
            LocalDate newDate = request.activityDate();
            int nextSequence = nextDailySequence(deal.getCreatedBy().getId(), newDate);

            auditService.record("DEAL", id, "activityDate", deal.getActivityDate(), newDate);
            deal.setActivityDate(newDate);
            deal.setWeek(weekService.requireByDate(newDate));
            deal.setDailySequence(nextSequence);
        }

        KanbanStage oldStage = deal.getKanbanStage();
        KanbanStage newStage = resolveStage(deal.isGoalAchieved(), deal.isProspective());
        deal.setKanbanStage(newStage);
        auditService.record("DEAL", id, "kanbanStage", oldStage, newStage);

        return mapper.deal(dealRepository.save(deal));
    }

    public Deal require(Long id) {
        return dealRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CRM-карточка не найдена: " + id));
    }

    private void replacePlacements(Deal deal, List<DealDtos.PlacementRequest> requests) {
        validatePlacements(requests);
        deal.getPlacements().clear();

        for (DealDtos.PlacementRequest request : requests) {
            City city = cityRepository.findById(request.cityId())
                    .orElseThrow(() -> new NotFoundException("Город не найден: " + request.cityId()));

            DealPlacement placement = new DealPlacement();
            placement.setDeal(deal);
            placement.setCity(city);
            placement.setStandFormat(request.standFormat());
            placement.setMediaType(com.adrotation.crm.crm.enums.MediaType.orDefault(request.mediaType()));
            placement.setPeriodStart(request.periodStart());
            placement.setPeriodEnd(request.periodEnd());
            placement.setQuantity(request.quantity());
            placement.setUnitPrice(request.unitPrice());
            placement.setDiscountPercent(request.discountPercent() == null ? BigDecimal.ZERO : request.discountPercent());
            placement.setPromotionName(sanitizer.plain(request.promotionName()));
            placement.setVatExempt(request.vatExempt() == null || request.vatExempt());
            placement.setVatRate(request.vatRate() == null ? BigDecimal.ZERO : request.vatRate());
            deal.getPlacements().add(placement);
        }
    }

    private void validatePlacements(List<DealDtos.PlacementRequest> requests) {
        for (DealDtos.PlacementRequest request : requests) {
            placementPolicy.validate(request.mediaType(), request.periodStart(), request.periodEnd());
            if (Boolean.TRUE.equals(request.vatExempt()) && request.vatRate() != null && request.vatRate().signum() > 0) {
                throw new BusinessException("Для размещения без НДС ставка НДС должна быть 0");
            }
        }
    }

    private Set<City> loadCities(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("Нужно выбрать хотя бы один целевой город");
        }

        List<City> cities = cityRepository.findAllById(ids);
        if (cities.size() != ids.size()) {
            throw new NotFoundException("Один или несколько целевых городов не найдены");
        }

        return new LinkedHashSet<>(cities);
    }

    private AppUser resolveManagerForCreate(AppUser current, Long requestedManagerId) {
        if (isManagerOnly(current)) {
            return current;
        }

        if (hasDealManagementRole(current) && requestedManagerId != null) {
            AppUser manager = userRepository.findById(requestedManagerId)
                    .orElseThrow(() -> new NotFoundException("Менеджер не найден: " + requestedManagerId));

            if (!manager.isEnabled() || !manager.getRoles().contains(Role.MANAGER)) {
                throw new BusinessException("Выбранный пользователь не является активным менеджером");
            }
            return manager;
        }

        return current;
    }

    private void assertCanEdit(Deal deal) {
        AppUser current = currentUserService.requireCurrentUser();

        if (hasDealManagementRole(current)) {
            return;
        }

        if (isManagerOnly(current) && Objects.equals(deal.getCreatedBy().getId(), current.getId())) {
            return;
        }

        throw new AccessDeniedException("Можно редактировать только свои CRM-карточки");
    }

    private boolean isManagerOnly(AppUser user) {
        return user.getRoles().contains(Role.MANAGER) && !hasDealManagementRole(user);
    }

    private boolean hasDealManagementRole(AppUser user) {
        return user.getRoles().contains(Role.SUPER_ADMIN)
                || user.getRoles().contains(Role.ROP)
                || user.getRoles().contains(Role.ROK);
    }

    private int nextDailySequence(Long managerId, LocalDate activityDate) {
        userRepository.findByIdForUpdate(managerId)
                .orElseThrow(() -> new NotFoundException("Менеджер не найден: " + managerId));
        return Math.addExact(dealRepository.maxDailySequence(managerId, activityDate), 1);
    }

    private KanbanStage resolveStage(boolean goalAchieved, boolean prospective) {
        if (goalAchieved) {
            return KanbanStage.GOING_TO_DEAL;
        }
        if (prospective) {
            return KanbanStage.PROSPECTIVE;
        }
        return KanbanStage.IN_WORK;
    }
}
