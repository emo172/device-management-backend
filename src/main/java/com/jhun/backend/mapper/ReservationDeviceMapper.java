package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.ReservationDevice;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 预约-设备关联持久层。
 * <p>
 * 负责维护预约聚合与设备的正式真相关系，并为历史单设备数据回填提供最小必要查询入口。
 */
@Mapper
public interface ReservationDeviceMapper extends BaseMapper<ReservationDevice> {

    /**
     * 查询某条预约当前关联的全部设备，并按业务顺序返回。
     */
    List<ReservationDevice> findByReservationId(@Param("reservationId") String reservationId);

    /**
     * 查找仍停留在旧单设备列、尚未拥有关联表记录的历史预约。
     */
    List<ReservationDevice> findLegacyReservationsWithoutRelation();

    /**
     * 统计单条预约当前的关联设备数量。
     */
    long countByReservationId(@Param("reservationId") String reservationId);
}
