package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.dto.role.RolePermissionTreeRow;
import com.jhun.backend.entity.Role;
import java.util.List;
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

    /**
     * 查询指定角色的权限树扁平结果。
     * <p>
     * 该方法会返回权限表中的全量权限，并额外标记目标角色当前是否已绑定，
     * 从而支持服务层按模块分组后构造成前端可直接消费的授权树。
     *
     * @param roleId 目标角色主键 ID
     * @return 扁平权限结果集，每一行对应一个权限节点
     */
    List<RolePermissionTreeRow> findPermissionTreeRows(@Param("roleId") String roleId);
}
