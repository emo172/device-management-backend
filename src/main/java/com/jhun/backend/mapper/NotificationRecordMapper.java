package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.NotificationRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 通知记录数据访问接口。
 */
@Mapper
public interface NotificationRecordMapper extends BaseMapper<NotificationRecord> {

    /** 查询用户通知列表。 */
    List<NotificationRecord> findByUserId(@Param("userId") String userId);

    /**
     * 分页查询用户通知列表。
     *
     * @param notificationType 可选通知类型筛选，按 notification_record.notification_type 精确匹配
     * @param userId 当前用户 ID
     * @param limit 每页条数
     * @param offset 偏移量
     * @return 当前页通知列表
     */
    List<NotificationRecord> findPageByConditions(
            @Param("notificationType") String notificationType,
            @Param("userId") String userId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    /**
     * 统计用户通知总数。
     *
     * @param notificationType 可选通知类型筛选
     * @param userId 当前用户 ID
     * @return 满足条件的通知总数
     */
    long countByConditions(@Param("notificationType") String notificationType, @Param("userId") String userId);

    /** 统计用户未读站内信数量。 */
    long countUnreadInAppByUserId(@Param("userId") String userId);

    /** 将单条站内信标记为已读。 */
    int markAsRead(
            @Param("notificationId") String notificationId,
            @Param("userId") String userId,
            @Param("readAt") LocalDateTime readAt);

    /** 将用户全部站内信标记为已读。 */
    int markAllAsRead(@Param("userId") String userId, @Param("readAt") LocalDateTime readAt);
}
