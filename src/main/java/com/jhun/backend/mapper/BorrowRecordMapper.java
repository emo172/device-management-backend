package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.BorrowRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 借还记录数据访问接口。
 * <p>
 * 除基础 CRUD 外，还提供按预约定位唯一借还记录与按角色可见范围查询列表的能力，
 * 以支撑“同一预约只能生成一条借还记录”和借还记录页查询。
 */
@Mapper
public interface BorrowRecordMapper extends BaseMapper<BorrowRecord> {

    BorrowRecord findByReservationId(@Param("reservationId") String reservationId);

    List<BorrowRecord> findPageByConditions(
            @Param("status") String status,
            @Param("userId") String userId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countByConditions(@Param("status") String status, @Param("userId") String userId);

    int updateReturnIfInBorrowedState(
            @Param("id") String id,
            @Param("returnTime") java.time.LocalDateTime returnTime,
            @Param("returnCheckStatus") String returnCheckStatus,
            @Param("remark") String remark,
            @Param("returnOperatorId") String returnOperatorId,
            @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
