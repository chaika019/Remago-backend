package com.adrotation.crm.notification.service;

import com.adrotation.crm.auth.security.CurrentUserService;
import com.adrotation.crm.notification.dto.NotificationDto;
import com.adrotation.crm.notification.entity.Notification;
import com.adrotation.crm.notification.enums.NotificationChannel;
import com.adrotation.crm.notification.enums.NotificationTriggerType;
import com.adrotation.crm.notification.repository.NotificationRepository;
import com.adrotation.crm.shared.exception.NotFoundException;
import com.adrotation.crm.shared.service.DtoMapper;
import com.adrotation.crm.user.entity.AppUser;
import com.adrotation.crm.user.enums.Role;
import com.adrotation.crm.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AppUserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final DtoMapper mapper;

    @Value("${app.notifications.default-channel:INTERNAL}")
    private NotificationChannel defaultChannel;

    @Transactional
    public void notifyUser(
            NotificationTriggerType triggerType,
            String entityType,
            Long entityId,
            AppUser user,
            String title,
            String payload
    ) {
        notifyUserWithKey(
                defaultKey(triggerType, entityType, entityId),
                triggerType,
                entityType,
                entityId,
                user,
                title,
                payload
        );
    }

    @Transactional
    public void notifyUserWithKey(
            String dedupeKey,
            NotificationTriggerType triggerType,
            String entityType,
            Long entityId,
            AppUser user,
            String title,
            String payload
    ) {
        if (user == null) {
            return;
        }
        if (dedupeKey != null
                && notificationRepository.existsByDedupeKeyAndRecipientUser_Id(dedupeKey, user.getId())) {
            return;
        }

        Notification notification = base(triggerType, entityType, entityId, title, payload);
        notification.setDedupeKey(dedupeKey);
        notification.setRecipientUser(user);
        saveAndDispatch(notification);
    }

    @Transactional
    public void notifyUserAlways(
            NotificationTriggerType triggerType,
            String entityType,
            Long entityId,
            AppUser user,
            String title,
            String payload
    ) {
        if (user == null) {
            return;
        }

        Notification notification = base(triggerType, entityType, entityId, title, payload);
        notification.setRecipientUser(user);
        saveAndDispatch(notification);
    }

    @Transactional
    public void notifyRole(
            NotificationTriggerType triggerType,
            String entityType,
            Long entityId,
            Role role,
            String title,
            String payload
    ) {
        notifyRoleWithKey(
                defaultKey(triggerType, entityType, entityId),
                triggerType,
                entityType,
                entityId,
                role,
                title,
                payload
        );
    }

    @Transactional
    public void notifyRoleWithKey(
            String dedupeKey,
            NotificationTriggerType triggerType,
            String entityType,
            Long entityId,
            Role role,
            String title,
            String payload
    ) {
        for (AppUser user : userRepository.findEnabledByRole(role)) {
            if (dedupeKey != null
                    && notificationRepository.existsByDedupeKeyAndRecipientUser_Id(dedupeKey, user.getId())) {
                continue;
            }

            Notification notification = base(triggerType, entityType, entityId, title, payload);
            notification.setDedupeKey(dedupeKey);
            notification.setRecipientUser(user);
            notification.setRecipientRole(role);
            saveAndDispatch(notification);
        }
    }

    @Transactional
    public void notifyRoleAlways(
            NotificationTriggerType triggerType,
            String entityType,
            Long entityId,
            Role role,
            String title,
            String payload
    ) {
        for (AppUser user : userRepository.findEnabledByRole(role)) {
            Notification notification = base(triggerType, entityType, entityId, title, payload);
            notification.setRecipientUser(user);
            notification.setRecipientRole(role);
            saveAndDispatch(notification);
        }
    }

    @Transactional
    public void notifyRolesAlways(
            NotificationTriggerType triggerType,
            String entityType,
            Long entityId,
            Collection<Role> roles,
            String title,
            String payload
    ) {
        roles.forEach(role -> notifyRoleAlways(
                triggerType,
                entityType,
                entityId,
                role,
                title,
                payload
        ));
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> currentUserFeed() {
        AppUser user = currentUserService.requireCurrentUser();
        List<Notification> notifications = user.getRoles().contains(Role.SUPER_ADMIN)
                ? notificationRepository.findAllByOrderByCreatedAtDesc()
                : notificationRepository.findForUser(user.getId(), user.getRoles());
        return notifications.stream()
                .map(mapper::notification)
                .toList();
    }

    @Transactional
    public NotificationDto markRead(Long id) {
        AppUser user = currentUserService.requireCurrentUser();
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Уведомление не найдено: " + id));

        boolean allowed = user.getRoles().contains(Role.SUPER_ADMIN)
                || (notification.getRecipientUser() != null
                ? notification.getRecipientUser().getId().equals(user.getId())
                : notification.getRecipientRole() != null
                && user.getRoles().contains(notification.getRecipientRole()));

        if (!allowed) {
            throw new AccessDeniedException("Уведомление не адресовано текущему пользователю");
        }

        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }

        return mapper.notification(notificationRepository.save(notification));
    }

    private String defaultKey(
            NotificationTriggerType triggerType,
            String entityType,
            Long entityId
    ) {
        return entityId == null
                ? null
                : triggerType.name() + ":" + entityType + ":" + entityId;
    }

    private Notification base(
            NotificationTriggerType triggerType,
            String entityType,
            Long entityId,
            String title,
            String payload
    ) {
        Notification notification = new Notification();
        notification.setTriggerType(triggerType);
        notification.setEntityType(entityType);
        notification.setEntityId(entityId);
        notification.setChannel(defaultChannel);
        notification.setTitle(title);
        notification.setPayload(payload);
        return notification;
    }

    private void saveAndDispatch(Notification notification) {
        notification.setSentAt(Instant.now());
        notificationRepository.save(notification);

        if (notification.getChannel() != NotificationChannel.INTERNAL) {
            log.warn(
                    "External notification channel {} is configured; notification was persisted in the internal inbox. "
                            + "A production delivery adapter is required for this channel.",
                    notification.getChannel()
            );
        }
    }
}
