package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 角色数据访问接口。
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * 按角色名称查询角色。
     *
     * @param name 角色名称
     * @return 角色实体
     */
    Role findByName(@Param("name") String name);
}
