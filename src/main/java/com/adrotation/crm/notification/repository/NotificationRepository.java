package com.adrotation.crm.notification.repository;

import com.adrotation.crm.notification.entity.Notification;
import com.adrotation.crm.user.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Query("select n from Notification n where (n.recipientUser.id = :userId or (n.recipientUser is null and n.recipientRole in :roles)) order by n.createdAt desc")
    List<Notification> findForUser(@Param("userId") Long userId, @Param("roles") Collection<Role> roles);

    List<Notification> findAllByOrderByCreatedAtDesc();

    boolean existsByDedupeKeyAndRecipientUser_Id(String dedupeKey, Long recipientUserId);
}
