package com.adrotation.crm.security.repository;

import com.adrotation.crm.security.entity.AuthAttempt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuthAttemptRepository extends JpaRepository<AuthAttempt, Long> {
    List<AuthAttempt> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<AuthAttempt> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
