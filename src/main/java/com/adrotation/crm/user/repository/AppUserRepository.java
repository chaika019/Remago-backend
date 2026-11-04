package com.adrotation.crm.user.repository;

import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.user.enums.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where lower(u.email) = lower(:email)")
    Optional<AppUser> findByEmailIgnoreCaseForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findByIdForUpdate(@Param("id") Long id);

    @Query("select distinct u from AppUser u join u.roles r where r = :role and u.enabled = true order by u.fullName")
    List<AppUser> findEnabledByRole(@Param("role") Role role);
}
