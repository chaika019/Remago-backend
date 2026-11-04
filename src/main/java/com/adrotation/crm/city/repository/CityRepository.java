package com.adrotation.crm.city.repository;

import com.adrotation.crm.city.entity.City;
import com.adrotation.crm.city.enums.CityCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long> {

    List<City> findAllByOrderByCategoryAscNameAsc();

    List<City> findByCategoryOrderByNameAsc(CityCategory category);

    List<City> findByNeedsRepairTrueOrderByNameAsc();

    Optional<City> findByNameIgnoreCase(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from City c where c.id = :id")
    Optional<City> findByIdForUpdate(@Param("id") Long id);
}
