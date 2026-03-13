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
 * C-04 借用确认超时处理任务。
 * <p>
 * 每 15 分钟扫描已签到但超过 2 小时未确认借用的预约，将其转为 PENDING_MANUAL，
 * 同时发送 BORROW_CONFIRM_WARNING 与 PENDING_MANUAL_NOTICE；
 * 对已处于 PENDING_MANUAL 且超过 24 小时未处理的预约执行自动取消兜底。
 */
@Component
public class BorrowConfirmTimeoutProcessor {

    private final ReservationMapper reservationMapper;
    private final NotificationRecordMapper notificationRecordMapper;

    public BorrowConfirmTimeoutProcessor(ReservationMapper reservationMapper, NotificationRecordMapper notificationRecordMapper) {
        this.reservationMapper = reservationMapper;
        this.notificationRecordMapper = notificationRecordMapper;
    }

    @Scheduled(cron = "0 */15 * * * ?")
    public void processBorrowConfirmTimeoutReservations() {
        List<Reservation> timedOutReservations = reservationMapper.findCheckedInBorrowConfirmTimeoutBefore(LocalDateTime.now().minusHours(2));
        for (Reservation reservation : timedOutReservations) {
            reservation.setStatus("PENDING_MANUAL");
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationMapper.updateById(reservation);
            saveNotification(
                    reservation.getUserId(),
                    "BORROW_CONFIRM_WARNING",
                    "借用确认超时",
                    "您已签到但设备管理员超过 2 小时未确认借用，预约已转入待人工处理",
                    reservation.getId());
            saveNotification(
                    reservation.getUserId(),
                    "PENDING_MANUAL_NOTICE",
                    "预约转待人工处理",
                    "您的预约已进入待人工处理状态，管理员将尽快处理",
                    reservation.getId());
        }

        List<Reservation> pendingManualExpiredReservations = reservationMapper.findPendingManualBefore(LocalDateTime.now().minusHours(24));
        for (Reservation reservation : pendingManualExpiredReservations) {
            reservation.setStatus("CANCELLED");
            reservation.setCancelReason("待人工处理超过 24 小时自动取消");
            reservation.setCancelTime(LocalDateTime.now());
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationMapper.updateById(reservation);
            saveNotification(
                    reservation.getUserId(),
                    "RESERVATION_CANCELLED",
                    "待人工处理超时自动取消",
                    "您的预约在待人工处理状态超过 24 小时，系统已自动取消",
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
