package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限数据访问接口。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
