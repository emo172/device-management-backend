package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.Reservation;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 预约数据访问接口。
 */
@Mapper
public interface ReservationMapper extends BaseMapper<Reservation> {

    List<Reservation> findConflictingReservations(
            @Param("deviceId") String deviceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /** 查询超过阈值仍处于审核中的预约。 */
    List<Reservation> findAuditPendingBefore(@Param("threshold") LocalDateTime threshold);

    /** 查询超过阈值仍未审核完成的预约（用于自动过期）。 */
    List<Reservation> findAuditPendingForExpireBefore(@Param("threshold") LocalDateTime threshold);

    /** 查询超过签到截止时间仍未签到的预约。 */
    List<Reservation> findApprovedNotCheckedInBefore(@Param("deadline") LocalDateTime deadline);

    /** 查询已签到且超过借用确认阈值的预约。 */
    List<Reservation> findCheckedInBorrowConfirmTimeoutBefore(@Param("deadline") LocalDateTime deadline);

    /** 查询待人工处理超过阈值的预约。 */
    List<Reservation> findPendingManualBefore(@Param("deadline") LocalDateTime deadline);

    /** 查询 30 分钟内即将开始且审批通过的预约。 */
    List<Reservation> findApprovedStartingBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** 查询指定设备未来审批通过的预约，用于设备维修通知。 */
    List<Reservation> findApprovedFutureReservationsByDeviceId(
            @Param("deviceId") String deviceId,
            @Param("start") LocalDateTime start);
}
