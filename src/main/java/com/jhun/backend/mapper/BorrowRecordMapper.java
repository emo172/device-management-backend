package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.BorrowRecord;
import java.time.LocalDateTime;
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

    /** 查询已超过预计归还时间、但尚未写入逾期状态的借还记录。 */
    List<BorrowRecord> findBorrowedExpectedReturnBefore(@Param("referenceTime") LocalDateTime referenceTime);

    /** 查询已经处于 OVERDUE 且尚未归还的借还记录，用于持续刷新逾期时长与升级冻结等级。 */
    List<BorrowRecord> findActiveOverdueRecords();

    /** 仅当记录仍处于 BORROWED 时才切换为 OVERDUE，避免重复检测时双写状态。 */
    int markOverdueIfBorrowed(@Param("id") String id, @Param("updatedAt") LocalDateTime updatedAt);

    /** 统计用户当前仍处于 OVERDUE 的借还记录数量，用于 C-07 自动释放限制。 */
    long countActiveOverdueByUserId(@Param("userId") String userId);

    int updateReturnIfInBorrowedState(
            @Param("id") String id,
            @Param("returnTime") LocalDateTime returnTime,
            @Param("returnCheckStatus") String returnCheckStatus,
            @Param("remark") String remark,
            @Param("returnOperatorId") String returnOperatorId,
            @Param("updatedAt") LocalDateTime updatedAt);
}
