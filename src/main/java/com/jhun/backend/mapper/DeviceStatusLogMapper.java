package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.DeviceStatusLog;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 设备状态日志数据访问接口。
 */
@Mapper
public interface DeviceStatusLogMapper extends BaseMapper<DeviceStatusLog> {

    List<DeviceStatusLog> findByDeviceId(@Param("deviceId") String deviceId);
}
