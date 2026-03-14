package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.DeviceCategory;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 设备分类数据访问接口。
 */
@Mapper
public interface DeviceCategoryMapper extends BaseMapper<DeviceCategory> {

    DeviceCategory findRootByName(@Param("name") String name);

    DeviceCategory findByParentIdAndName(@Param("parentId") String parentId, @Param("name") String name);

    List<DeviceCategory> findAllOrderBySort();
}
