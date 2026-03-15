package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.OverdueRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 逾期记录数据访问接口。
 * <p>
 * 除基础 CRUD 外，还负责支撑逾期列表分页、按借还记录定位唯一逾期条目、更新检测时长快照、
 * 标记通知已发送与写入管理员处理结果，避免服务层把 SQL 细节散落到多个任务实现中。
 */
@Mapper
public interface OverdueRecordMapper extends BaseMapper<OverdueRecord> {

    /**
     * 按借还记录 ID 查询唯一逾期记录。
     *
     * @param borrowRecordId 借还记录 ID
     * @return 对应逾期记录，不存在时返回空
     */
    OverdueRecord findByBorrowRecordId(@Param("borrowRecordId") String borrowRecordId);

    /**
     * 分页查询逾期记录。
     *
     * @param processingStatus 可选处理状态筛选
     * @param userId 普通用户视角下的可见用户 ID；管理视角传空
     * @param limit 每页条数
     * @param offset 偏移量
     * @return 当前页逾期记录列表
     */
    List<OverdueRecord> findPageByConditions(
            @Param("processingStatus") String processingStatus,
            @Param("userId") String userId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * 统计逾期记录总数。
     *
     * @param processingStatus 可选处理状态筛选
     * @param userId 普通用户视角下的可见用户 ID；管理视角传空
     * @return 满足条件的逾期记录总数
     */
    long countByConditions(@Param("processingStatus") String processingStatus, @Param("userId") String userId);

    /**
     * 查询尚未发送正式提醒、且对应借还记录仍处于 OVERDUE 的逾期记录。
     *
     * @return 待发送提醒的逾期记录列表
     */
    List<OverdueRecord> findNotificationPendingRecords();

    /**
     * 刷新逾期时长快照。
     *
     * @param id 逾期记录 ID
     * @param overdueHours 最新逾期小时数
     * @param overdueDays 最新逾期天数字段
     * @param updatedAt 更新时间
     * @return 受影响行数
     */
    int updateDuration(
            @Param("id") String id,
            @Param("overdueHours") int overdueHours,
            @Param("overdueDays") int overdueDays,
            @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 直接更新通知发送标记。
     * <p>
     * 该方法保留给非并发敏感的内部修复场景使用；C-06 正式链路应优先使用 CAS 版本。
     */
    int updateNotificationSent(
            @Param("id") String id,
            @Param("notificationSent") int notificationSent,
            @Param("updatedAt") LocalDateTime updatedAt);

    /** 仅当 notification_sent 仍为 0 时才更新为已发送，用于 C-06 的 CAS 幂等保护。 */
    int updateNotificationSentIfPending(
            @Param("id") String id,
            @Param("notificationSent") int notificationSent,
            @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 写入设备管理员处理结果。
     *
     * @param id 逾期记录 ID
     * @param processingMethod 处理方式
     * @param processingRemark 处理备注
     * @param processorId 处理人 ID
     * @param processingTime 处理时间
     * @param compensationAmount 赔偿金额
     * @param updatedAt 更新时间
     * @return 受影响行数；为 0 表示记录已非待处理状态
     */
    int updateProcessingResult(
            @Param("id") String id,
            @Param("processingMethod") String processingMethod,
            @Param("processingRemark") String processingRemark,
            @Param("processorId") String processorId,
            @Param("processingTime") LocalDateTime processingTime,
            @Param("compensationAmount") java.math.BigDecimal compensationAmount,
            @Param("updatedAt") LocalDateTime updatedAt);
}
