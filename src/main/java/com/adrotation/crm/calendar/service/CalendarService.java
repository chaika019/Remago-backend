package com.adrotation.crm.calendar.service;

import com.adrotation.crm.auth.security.CurrentUserService;
import com.adrotation.crm.calendar.dto.CalendarDtos;
import com.adrotation.crm.calendar.entity.CalendarEntry;
import com.adrotation.crm.calendar.repository.CalendarEntryRepository;
import com.adrotation.crm.audit.service.AuditService;
import com.adrotation.crm.shared.service.TextSanitizer;
import com.adrotation.crm.shared.exception.NotFoundException;
import com.adrotation.crm.crm.entity.Deal;
import com.adrotation.crm.crm.repository.DealRepository;
import com.adrotation.crm.schedule.entity.Shift;
import com.adrotation.crm.schedule.repository.ShiftRepository;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.user.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CalendarService {
    private final ShiftRepository shiftRepository;
    private final DealRepository dealRepository;
    private final CurrentUserService currentUserService;
    private final CalendarEntryRepository entryRepository;
    private final TextSanitizer sanitizer;
    private final AuditService auditService;

    @Transactional
    public CalendarDtos.Event create(CalendarDtos.SaveRequest request) {
        CalendarEntry entry = new CalendarEntry();
        apply(entry, request);
        entryRepository.save(entry);
        auditService.record("CALENDAR_EVENT", entry.getId(), "created", null, event(entry));
        return event(entry);
    }

    @Transactional
    public CalendarDtos.Event update(Long id, CalendarDtos.SaveRequest request) {
        CalendarEntry entry = requireEntry(id);
        CalendarDtos.Event before = event(entry);
        apply(entry, request);
        auditService.record("CALENDAR_EVENT", id, "updated", before, event(entry));
        return event(entry);
    }

    @Transactional
    public void delete(Long id) {
        CalendarEntry entry = requireEntry(id);
        auditService.record("CALENDAR_EVENT", id, "deleted", event(entry), null);
        entryRepository.delete(entry);
    }

    private CalendarEntry requireEntry(Long id) {
        return entryRepository.findById(id).orElseThrow(() -> new NotFoundException("Событие не найдено: " + id));
    }

    private void apply(CalendarEntry entry, CalendarDtos.SaveRequest request) {
        String title = sanitizer.plain(request.title());
        if (title == null || title.isBlank()) throw new BusinessException("Укажите название события");
        entry.setTitle(title);
        entry.setDescription(sanitizer.plain(request.description()));
        entry.setEventDate(request.date());
    }

    private CalendarDtos.Event event(CalendarEntry entry) {
        return new CalendarDtos.Event("EVENT-" + entry.getId(), "MANUAL", entry.getEventDate(),
                entry.getTitle(), entry.getDescription(), null, Map.of("eventId", entry.getId()));
    }

    @Transactional(readOnly = true)
    public CalendarDtos.Response calendar(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) throw new BusinessException("Некорректный период календаря");
        if (from.plusYears(1).isBefore(to)) throw new BusinessException("Период календаря не может превышать один год");
        AppUser user = currentUserService.requireCurrentUser();
        List<CalendarDtos.Event> events = new ArrayList<>();
        entryRepository.findByEventDateBetweenOrderByEventDateAscIdAsc(from, to)
                .stream().map(this::event).forEach(events::add);

        for (Shift s : shiftRepository.findForCalendar(from, to)) {
            LocalDate date = s.getWeek().getStartDate();
            events.add(new CalendarDtos.Event("SHIFT-" + s.getId(), "SHIFT", date,
                    s.getCity().getName() + " · " + s.getStatus().getDisplayName(),
                    s.getCompany() == null ? "Ротация" : s.getCompany().getCanonicalName(),
                    "/schedule?shift=" + s.getId(),
                    Map.of("shiftId", s.getId(), "status", s.getStatus().name(), "color", s.getStatus().getColorHex())));
        }

        if (canViewCrm(user)) {
            Long managerId = isManagerOnly(user) ? user.getId() : null;
            for (Deal d : dealRepository.findCalendarDeals(from, to, managerId)) {
                if (d.getNextCallDate() != null && !d.getNextCallDate().isBefore(from) && !d.getNextCallDate().isAfter(to)) {
                    events.add(new CalendarDtos.Event("CALL-" + d.getId(), "CALLBACK", d.getNextCallDate(),
                            "Перезвон · " + d.getCompany().getCanonicalName(), d.getComment(), "/crm?deal=" + d.getId(),
                            Map.of("dealId", d.getId(), "managerId", d.getCreatedBy().getId())));
                }
                if (d.getNextActionDueDate() != null && d.getNextActionCompletedAt() == null && !d.getNextActionDueDate().isBefore(from) && !d.getNextActionDueDate().isAfter(to)) {
                    events.add(new CalendarDtos.Event("ACTION-" + d.getId(), "NEXT_ACTION", d.getNextActionDueDate(),
                            d.getNextAction() == null ? "Следующее действие" : d.getNextAction(), d.getCompany().getCanonicalName(), "/crm?deal=" + d.getId(),
                            Map.of("dealId", d.getId(), "overdue", d.getNextActionDueDate().isBefore(LocalDate.now()))));
                }
            }
        }
        events.sort(Comparator.comparing(CalendarDtos.Event::date).thenComparing(CalendarDtos.Event::type));
        return new CalendarDtos.Response(from, to, events);
    }

    private boolean canViewCrm(AppUser u) {
        return u.getRoles().stream().anyMatch(r -> r == Role.SUPER_ADMIN || r == Role.DIRECTOR || r == Role.ROP || r == Role.ROK || r == Role.MANAGER);
    }
    private boolean isManagerOnly(AppUser u) {
        return u.getRoles().contains(Role.MANAGER) && u.getRoles().stream().noneMatch(r -> r == Role.SUPER_ADMIN || r == Role.DIRECTOR || r == Role.ROP || r == Role.ROK);
    }
}
