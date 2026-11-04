package com.adrotation.crm.crm.repository;

import com.adrotation.crm.crm.entity.DealPlacement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DealPlacementRepository extends JpaRepository<DealPlacement, Long> {
    List<DealPlacement> findByDeal_IdOrderByPeriodStartAscIdAsc(Long dealId);
}
