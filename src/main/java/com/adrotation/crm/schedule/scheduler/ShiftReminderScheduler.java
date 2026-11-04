package com.adrotation.crm.schedule.scheduler;

import com.adrotation.crm.schedule.entity.Shift;
import com.adrotation.crm.notification.enums.NotificationTriggerType;
import com.adrotation.crm.schedule.enums.ShiftStatus;
import com.adrotation.crm.schedule.repository.ShiftRepository;
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
public class ShiftReminderScheduler {
    private final ShiftRepository shiftRepository;
    private final NotificationService notificationService;

    @Value("${app.time-zone:Europe/Minsk}")
    private String timeZone;

    @Scheduled(cron = "0 5 8 * * *", zone = "${app.time-zone:Europe/Minsk}")
    @Transactional
    public void remindSellingCycle() {
        LocalDate today = LocalDate.now(ZoneId.of(timeZone));
        LocalDate latestStart = today.plusDays(21);
        for (Shift shift : shiftRepository.findByStatusAndWeekStartDateBetween(ShiftStatus.SHIFT_AND_SALE, today, latestStart)) {
            if (!shift.getCity().isRotationEnabled() || shift.getCity().isNeedsRepair() || shift.getResponsiblePerson() == null) continue;
            notificationService.notifyUser(
                    NotificationTriggerType.SELL_CITY_REMINDER,
                    "SHIFT",
                    shift.getId(),
                    shift.getResponsiblePerson(),
                    "Продаём этот город",
                    "До плановой смены в городе " + shift.getCity().getName() + " осталось не более 3 недель. Начните активные продажи."
            );
        }
    }
}
