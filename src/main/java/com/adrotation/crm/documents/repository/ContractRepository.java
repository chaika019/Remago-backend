package com.adrotation.crm.documents.repository;

import com.adrotation.crm.documents.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findByCompany_IdOrderByContractDateDesc(Long companyId);

    List<Contract> findByCompany_IdAndActiveTrueOrderByContractDateDesc(Long companyId);

    boolean existsByCompany_IdAndNumberIgnoreCase(Long companyId, String number);
    java.util.Optional<Contract> findByCompany_IdAndNumberIgnoreCase(Long companyId, String number);
    List<Contract> findTop20ByNumberContainingIgnoreCaseOrderByContractDateDesc(String number);
}
