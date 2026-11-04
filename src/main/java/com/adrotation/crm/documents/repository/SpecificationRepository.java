package com.adrotation.crm.documents.repository;
import com.adrotation.crm.documents.entity.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SpecificationRepository extends JpaRepository<Specification, Long> {
    java.util.Optional<Specification> findByNumber(Long number);
    List<Specification> findByDeal_IdOrderByCreatedAtDesc(Long dealId);
    List<Specification> findByCompany_IdOrderBySpecificationDateDesc(Long companyId);
    List<Specification> findTop20ByNumberGreaterThanEqualOrderByNumberAsc(Long number);
}
