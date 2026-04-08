package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.Device;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 设备数据访问接口。
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    Device findByDeviceNumber(@Param("deviceNumber") String deviceNumber);

    List<Device> findActiveDevices(@Param("categoryId") String categoryId);

    /**
     * 分页查询目标时间窗内可预约的设备。
     * <p>
     * 该查询同时承担静态状态过滤、关键字搜索、分类范围收敛与时间冲突排除，避免创建设备预约页先拉全量设备再在前端本地过滤。
     */
    List<Device> findReservableDevices(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("keyword") String keyword,
            @Param("categoryIds") List<String> categoryIds,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * 统计目标时间窗内可预约设备总数。
     * <p>
     * 统计口径必须与分页查询完全一致，否则创建设备预约页会出现总数和当前页记录不一致的问题。
     */
    long countReservableDevices(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("keyword") String keyword,
            @Param("categoryIds") List<String> categoryIds);

    int updateStatusIfCurrent(
            @Param("deviceId") String deviceId,
            @Param("expectedStatus") String expectedStatus,
            @Param("newStatus") String newStatus,
            @Param("reason") String reason);
}
