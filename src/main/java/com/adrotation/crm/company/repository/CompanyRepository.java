package com.adrotation.crm.company.repository;

import com.adrotation.crm.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    List<Company> findTop20ByCanonicalNameContainingIgnoreCaseOrderByCanonicalNameAsc(String query);
    List<Company> findTop50ByShortNameContainingIgnoreCaseOrderByShortNameAsc(String query);
    Optional<Company> findByCanonicalNameIgnoreCase(String canonicalName);
    Optional<Company> findByInn(String inn);
}
