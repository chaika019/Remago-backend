package com.adrotation.crm.calendar.service;

import com.adrotation.crm.audit.service.AuditService;
import com.adrotation.crm.auth.security.CurrentUserService;
import com.adrotation.crm.calendar.dto.CalendarDtos;
import com.adrotation.crm.calendar.entity.CalendarEntry;
import com.adrotation.crm.calendar.repository.CalendarEntryRepository;
import com.adrotation.crm.crm.repository.DealRepository;
import com.adrotation.crm.schedule.repository.ShiftRepository;
import com.adrotation.crm.shared.exception.BusinessException;
import com.adrotation.crm.shared.exception.NotFoundException;
import com.adrotation.crm.shared.service.TextSanitizer;
import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.user.enums.Role;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalendarServiceTest {
    private final CalendarEntryRepository entries = mock(CalendarEntryRepository.class);
    private final ShiftRepository shifts = mock(ShiftRepository.class);
    private final DealRepository deals = mock(DealRepository.class);
    private final CurrentUserService current = mock(CurrentUserService.class);
    private final AuditService audit = mock(AuditService.class);
    private final CalendarService service = new CalendarService(shifts, deals, current, entries, new TextSanitizer(), audit);
    private final LocalDate date = LocalDate.of(2026, 9, 16);

    @Test void createUpdateListAndDeleteManualEvent() {
        when(entries.save(any())).thenAnswer(invocation -> { CalendarEntry e = invocation.getArgument(0); e.setId(12L); return e; });
        CalendarDtos.Event result = service.create(new CalendarDtos.SaveRequest("<b>Встреча</b>", "Описание", date));
        assertEquals("Встреча", result.title());
        assertEquals("MANUAL", result.type());
        assertEquals(12L, result.meta().get("eventId"));
        var captor = org.mockito.ArgumentCaptor.forClass(CalendarEntry.class);
        verify(entries).save(captor.capture());
        CalendarEntry entry = captor.getValue();
        when(entries.findById(12L)).thenReturn(Optional.of(entry));
        assertEquals(date.plusDays(1), service.update(12L, new CalendarDtos.SaveRequest("Новое", "", date.plusDays(1))).date());
        AppUser user = new AppUser(); user.setRoles(Set.of(Role.SUPER_ADMIN));
        when(current.requireCurrentUser()).thenReturn(user);
        when(entries.findByEventDateBetweenOrderByEventDateAscIdAsc(date, date.plusDays(2))).thenReturn(List.of(entry));
        assertEquals(1, service.calendar(date, date.plusDays(2)).events().size());
        service.delete(12L);
        verify(entries).delete(entry);
        verify(audit).record(eq("CALENDAR_EVENT"), eq(12L), eq("deleted"), any(), isNull());
    }

    @Test void rejectsEmptySanitizedTitlesAndInvalidRanges() {
        assertThrows(BusinessException.class, () -> service.create(new CalendarDtos.SaveRequest("<b></b>", null, date)));
        assertThrows(BusinessException.class, () -> service.calendar(date, date.minusDays(1)));
        assertThrows(BusinessException.class, () -> service.calendar(date, date.plusYears(2)));
        verifyNoInteractions(entries);
    }

    @Test void missingEventIsNotFound() {
        assertThrows(NotFoundException.class, () -> service.delete(999L));
        assertThrows(NotFoundException.class, () -> service.update(999L, new CalendarDtos.SaveRequest("Title", null, date)));
    }
}
