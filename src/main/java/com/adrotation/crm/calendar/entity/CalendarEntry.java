package com.adrotation.crm.calendar.entity;

import com.adrotation.crm.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "calendar_entries")
@Getter
@Setter
public class CalendarEntry extends BaseEntity {
    @Column(nullable = false, length = 200)
    private String title;
    @Column(length = 4000)
    private String description;
    @Column(nullable = false)
    private LocalDate eventDate;
}
