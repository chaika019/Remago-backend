package com.adrotation.crm.calendar.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CalendarDtos {
    private CalendarDtos() {}
    public record SaveRequest(@NotBlank @Size(max = 200) String title,
                              @Size(max = 4000) String description,
                              @NotNull LocalDate date) {}
    public record Event(String id, String type, LocalDate date, String title, String subtitle, String route, Map<String, Object> meta) {}
    public record Response(LocalDate from, LocalDate to, List<Event> events) {}
}
