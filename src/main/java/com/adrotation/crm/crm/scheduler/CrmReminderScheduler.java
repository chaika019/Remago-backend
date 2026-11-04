package com.adrotation.crm.crm.scheduler;

import com.adrotation.crm.crm.entity.Deal;
import com.adrotation.crm.notification.enums.NotificationTriggerType;
import com.adrotation.crm.crm.repository.DealRepository;
import com.adrotation.crm.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import java.time.ZoneId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class CrmReminderScheduler {
    private final DealRepository dealRepository;
    private final NotificationService notificationService;

    @Value("${app.time-zone:Europe/Minsk}")
    private String timeZone;

    @Scheduled(cron = "0 0 8 * * *", zone = "${app.time-zone:Europe/Minsk}")
    @Transactional
    public void remindNextCalls() {
        LocalDate today = LocalDate.now(ZoneId.of(timeZone));
        for (Deal deal : dealRepository.findByNextCallDate(today)) {
            notificationService.notifyUserWithKey(
                    "NEXT_CALL_DUE:DEAL:" + deal.getId() + ":" + today,
                    NotificationTriggerType.NEXT_CALL_DUE,
                    "DEAL",
                    deal.getId(),
                    deal.getCreatedBy(),
                    "Сегодня нужно перезвонить",
                    deal.getCompany().getCanonicalName() + ": " + (deal.getComment() == null ? "без комментария" : deal.getComment())
            );
        }
    }
}
