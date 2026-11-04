package com.adrotation.crm.security.repository;

import com.adrotation.crm.security.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {
    List<UserSession> findByUser_IdOrderByLastSeenAtDesc(Long userId);

    @Modifying
    @Query("update UserSession s set s.revokedAt = :now where s.user.id = :userId and s.revokedAt is null and s.id <> :exceptId")
    int revokeAllExcept(@Param("userId") Long userId, @Param("exceptId") String exceptId, @Param("now") Instant now);

    @Modifying
    @Query("update UserSession s set s.revokedAt = :now where s.user.id = :userId and s.revokedAt is null")
    int revokeAll(@Param("userId") Long userId, @Param("now") Instant now);
}
