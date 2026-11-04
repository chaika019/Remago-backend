package com.adrotation.crm.security.repository;

import com.adrotation.crm.security.entity.TwoFactorChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TwoFactorChallengeRepository extends JpaRepository<TwoFactorChallenge, String> {}
