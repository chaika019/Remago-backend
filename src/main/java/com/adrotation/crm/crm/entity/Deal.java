package com.adrotation.crm.crm.entity;

import com.adrotation.crm.city.entity.City;
import com.adrotation.crm.company.entity.Company;
import com.adrotation.crm.crm.enums.CalledToday;
import com.adrotation.crm.crm.enums.DocumentIntent;
import com.adrotation.crm.crm.enums.KanbanStage;
import com.adrotation.crm.crm.enums.ServiceType;
import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.schedule.entity.WeekPeriod;
import com.adrotation.crm.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Getter
@Setter
@Entity
@Table(name = "deals", uniqueConstraints = @UniqueConstraint(name = "uk_deal_daily_sequence", columnNames = {"created_by_id", "activity_date", "daily_sequence"}))
public class Deal extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id", nullable = false) private Company company;

    @ManyToMany
    @JoinTable(name = "deal_target_cities", joinColumns = @JoinColumn(name = "deal_id"), inverseJoinColumns = @JoinColumn(name = "city_id"))
    private Set<City> targetCities = new LinkedHashSet<>();

    @OneToMany(mappedBy = "deal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("periodStart asc, id asc")
    private List<DealPlacement> placements = new ArrayList<>();

    @Enumerated(EnumType.STRING) @Column(nullable = false) private ServiceType serviceType;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "goal_option_id") private GoalOption dailyGoal;
    @Column(nullable = false) private boolean goalAchieved;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CalledToday calledToday = CalledToday.NOT_CALLED;

    // Явно фиксируем результат коммуникации для автоматизации документов.
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DocumentIntent documentIntent = DocumentIntent.NONE;

    private LocalDate nextCallDate;
    @Column(length = 500) private String nextAction;
    private LocalDate nextActionDueDate;
    private Instant nextActionCompletedAt;
    @Column(columnDefinition = "text") private String comment;
    @Column(nullable = false) private boolean prospective;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private KanbanStage kanbanStage = KanbanStage.IN_WORK;
    @Column(name = "activity_date", nullable = false) private LocalDate activityDate;
    @Column(name = "daily_sequence", nullable = false) private int dailySequence;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "week_id", nullable = false) private WeekPeriod week;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by_id", nullable = false) private AppUser createdBy;
}
