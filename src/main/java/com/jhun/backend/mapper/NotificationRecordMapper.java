package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.NotificationRecord;
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

    /** 统计用户未读站内信数量。 */
    long countUnreadInAppByUserId(@Param("userId") String userId);

    /** 将单条站内信标记为已读。 */
    int markAsRead(@Param("notificationId") String notificationId, @Param("userId") String userId);

    /** 将用户全部站内信标记为已读。 */
    int markAllAsRead(@Param("userId") String userId);
}
