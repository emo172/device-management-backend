package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色权限关联数据访问接口。
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {
}
