package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.Device;
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
}
