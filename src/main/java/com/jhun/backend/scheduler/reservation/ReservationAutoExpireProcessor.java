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
 * C-02 预约自动过期任务。
 * <p>
 * 每小时处理超过 72 小时仍未审核完成的预约，统一转为 EXPIRED 并发送 APPROVAL_EXPIRED 通知。
 */
@Component
public class ReservationAutoExpireProcessor {

    private final ReservationMapper reservationMapper;
    private final NotificationRecordMapper notificationRecordMapper;

    public ReservationAutoExpireProcessor(ReservationMapper reservationMapper, NotificationRecordMapper notificationRecordMapper) {
        this.reservationMapper = reservationMapper;
        this.notificationRecordMapper = notificationRecordMapper;
    }

    @Scheduled(cron = "0 15 * * * ?")
    public void processAutoExpireReservations() {
        List<Reservation> reservations = reservationMapper.findAuditPendingForExpireBefore(LocalDateTime.now().minusHours(72));
        for (Reservation reservation : reservations) {
            reservation.setStatus("EXPIRED");
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationMapper.updateById(reservation);
            saveNotification(
                    reservation.getUserId(),
                    "APPROVAL_EXPIRED",
                    "预约审核超时自动过期",
                    "您的预约已超过 72 小时未完成审核，系统已自动过期",
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
