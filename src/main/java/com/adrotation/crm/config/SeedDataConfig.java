package com.adrotation.crm.config;

import com.adrotation.crm.crm.entity.GoalOption;
import com.adrotation.crm.crm.repository.GoalOptionRepository;
import com.adrotation.crm.schedule.service.WeekService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class SeedDataConfig implements ApplicationRunner {

    private final GoalOptionRepository goalRepository;
    private final WeekService weekService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createGoal("КП наружка");
        createGoal("Договор · счёт");
        createGoal("Уведомление");
        weekService.syncYear(2026);
    }

    private void createGoal(String name) {
        if (goalRepository.existsByNameIgnoreCase(name)) {
            return;
        }

        GoalOption goal = new GoalOption();
        goal.setName(name);
        goal.setActive(true);
        goalRepository.save(goal);
    }
}
