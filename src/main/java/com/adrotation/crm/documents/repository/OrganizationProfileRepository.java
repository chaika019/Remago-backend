package com.adrotation.crm.documents.repository;

import com.adrotation.crm.documents.entity.OrganizationProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationProfileRepository extends JpaRepository<OrganizationProfile, Long> {
    Optional<OrganizationProfile> findFirstByOrderByIdAsc();
}
