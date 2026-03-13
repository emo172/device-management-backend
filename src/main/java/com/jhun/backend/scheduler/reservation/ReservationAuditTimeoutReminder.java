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
 * C-01 预约审核超时提醒任务。
 * <p>
 * 每小时扫描超过 48 小时仍处于待审核状态的预约，发送 REVIEW_TIMEOUT_WARNING 站内信，
 * 降低审批长时间积压造成的业务滞留。
 */
@Component
public class ReservationAuditTimeoutReminder {

    private final ReservationMapper reservationMapper;
    private final NotificationRecordMapper notificationRecordMapper;

    public ReservationAuditTimeoutReminder(ReservationMapper reservationMapper, NotificationRecordMapper notificationRecordMapper) {
        this.reservationMapper = reservationMapper;
        this.notificationRecordMapper = notificationRecordMapper;
    }

    @Scheduled(cron = "0 0 * * * ?")
    public void remindAuditTimeoutReservations() {
        List<Reservation> reservations = reservationMapper.findAuditPendingBefore(LocalDateTime.now().minusHours(48));
        for (Reservation reservation : reservations) {
            saveNotification(
                    reservation.getUserId(),
                    "REVIEW_TIMEOUT_WARNING",
                    "预约审核超时提醒",
                    "您的预约已等待审核超过 48 小时，请关注审核进度",
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
