package com.adrotation.crm.crm.repository;

import com.adrotation.crm.crm.entity.SavedView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SavedViewRepository extends JpaRepository<SavedView, Long> {
    @Query("""
            select v from SavedView v
            where v.module = :module and (v.user.id = :userId or v.shared = true)
            order by v.createdAt desc
            """)
    List<SavedView> findVisible(@Param("module") String module, @Param("userId") Long userId);

    boolean existsByUser_IdAndModuleAndNameIgnoreCase(Long userId, String module, String name);
}
