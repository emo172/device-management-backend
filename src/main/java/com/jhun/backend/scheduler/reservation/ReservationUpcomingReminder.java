package com.jhun.backend.scheduler.reservation;

import com.jhun.backend.entity.NotificationRecord;
import com.jhun.backend.entity.Reservation;
import com.jhun.backend.mapper.NotificationRecordMapper;
import com.jhun.backend.mapper.ReservationMapper;
import com.jhun.backend.util.UuidUtil;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * C-11 预约即将开始提醒任务。
 * <p>
 * 每 15 分钟提醒未来 30 分钟内开始的已审批预约，帮助用户按时签到。
 */
@Component
public class ReservationUpcomingReminder {

    private final ReservationMapper reservationMapper;
    private final NotificationRecordMapper notificationRecordMapper;

    public ReservationUpcomingReminder(ReservationMapper reservationMapper, NotificationRecordMapper notificationRecordMapper) {
        this.reservationMapper = reservationMapper;
        this.notificationRecordMapper = notificationRecordMapper;
    }

    @Scheduled(cron = "0 */15 * * * ?")
    public void remindUpcomingReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> reservations = reservationMapper.findApprovedStartingBetween(now, now.plusMinutes(30));
        for (Reservation reservation : reservations) {
            saveNotification(
                    reservation.getUserId(),
                    "RESERVATION_REMINDER",
                    "预约即将开始",
                    "您的预约将在 30 分钟内开始，请及时签到",
                    reservation.getId());
        }
    }

    private void saveNotification(String userId, String type, String title, String content, String reservationId) {
        NotificationRecord record = new NotificationRecord();
        record.setId(UuidUtil.randomUuid());
        record.setUserId(userId);
        record.setNotificationType(type);
        record.setChannel("IN_APP");
        record.setTitle(title);
        record.setContent(content);
        record.setStatus("SUCCESS");
        record.setRetryCount(0);
        record.setReadFlag(0);
        record.setRelatedId(reservationId);
        record.setRelatedType("RESERVATION");
        notificationRecordMapper.insert(record);
    }
}
