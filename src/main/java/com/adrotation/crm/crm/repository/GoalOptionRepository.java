package com.adrotation.crm.crm.repository;

import com.adrotation.crm.crm.entity.GoalOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalOptionRepository extends JpaRepository<GoalOption, Long> {
    List<GoalOption> findByActiveTrueOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
