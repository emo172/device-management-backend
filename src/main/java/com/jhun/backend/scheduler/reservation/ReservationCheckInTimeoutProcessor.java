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
 * C-03 签到超时处理任务。
 * <p>
 * 每 15 分钟处理开始后超过 60 分钟仍未签到的预约，转为 EXPIRED 并发送 CHECKIN_TIMEOUT_WARNING 通知。
 */
@Component
public class ReservationCheckInTimeoutProcessor {

    private final ReservationMapper reservationMapper;
    private final NotificationRecordMapper notificationRecordMapper;

    public ReservationCheckInTimeoutProcessor(ReservationMapper reservationMapper, NotificationRecordMapper notificationRecordMapper) {
        this.reservationMapper = reservationMapper;
        this.notificationRecordMapper = notificationRecordMapper;
    }

    @Scheduled(cron = "0 */15 * * * ?")
    public void processCheckInTimeoutReservations() {
        List<Reservation> reservations = reservationMapper.findApprovedNotCheckedInBefore(LocalDateTime.now().minusMinutes(60));
        for (Reservation reservation : reservations) {
            reservation.setStatus("EXPIRED");
            reservation.setSignStatus("CHECKED_IN_TIMEOUT");
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationMapper.updateById(reservation);
            saveNotification(
                    reservation.getUserId(),
                    "CHECKIN_TIMEOUT_WARNING",
                    "签到超时提醒",
                    "您的预约超过签到窗口未签到，系统已自动过期",
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
