package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.Reservation;
import com.jhun.backend.entity.ReservationDevice;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 预约数据访问接口。
 */
@Mapper
public interface ReservationMapper extends BaseMapper<Reservation> {

    /**
     * 分页查询预约列表。
     * <p>
     * 当 userId 传入时仅返回该用户本人预约；当 userId 为空时返回管理视角可见的预约列表。
     */
    List<Reservation> findPageByConditions(
            @Param("userId") String userId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * 统计预约列表总数。
     * <p>
     * 统计口径必须与列表查询保持一致，否则前端分页会出现总数与当前页不匹配的问题。
     */
    long countByConditions(@Param("userId") String userId);

    /**
     * 按聚合读模型查询单条预约。
     * <p>
     * 该方法会从 {@code reservation_device} 恢复主设备兼容字段，避免 cutover 后继续依赖旧列读取设备信息。
     */
    Reservation findAggregateById(@Param("reservationId") String reservationId);

    /**
     * 批量查询预约关联设备顺序。
     * <p>
     * T4 读模型扩展后，列表、详情与动作回包都要以这里返回的 `device_order` 顺序组装 `devices[]`
     * 和主设备兼容字段，避免再次退回旧 `reservation.device_id` 真相。
     */
    List<ReservationDevice> findDeviceRelationsByReservationIds(@Param("reservationIds") Collection<String> reservationIds);

    List<Reservation> findConflictingReservations(
            @Param("deviceId") String deviceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定设备在给定时间段内的有效预约数量。
     * <p>
     * 该方法主要服务并发冲突回归测试，用于确认高并发下数据库最终只落下一条会占用时间段的预约记录，
     * 防止应用层虽然抛出了冲突异常，但库内仍残留重复数据。
     */
    long countActiveReservationsByDeviceAndTimeRange(
            @Param("deviceId") String deviceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 按当前数据库状态原子取消预约。
     * <p>
     * 该方法要求预约仍处于可取消状态、尚未签到且尚未开始，避免“先查后改”在并发下覆盖审批、签到等新状态。
     */
    int cancelReservationSafely(
            @Param("reservationId") String reservationId,
            @Param("cancelReason") String cancelReason,
            @Param("cancelTime") LocalDateTime cancelTime,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("currentTime") LocalDateTime currentTime,
            @Param("allowedStatuses") List<String> allowedStatuses);

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
