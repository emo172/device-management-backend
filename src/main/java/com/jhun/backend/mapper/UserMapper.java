package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.User;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户数据访问接口。
 * <p>
 * 当前阶段负责认证链所需的账号查询、邮箱查询和带角色信息的本人资料查询。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 按用户名或邮箱查找用户。
     *
     * @param account 登录账号，支持用户名或邮箱
     * @return 用户实体，不存在时返回空
     */
    User findByAccount(@Param("account") String account);

    /**
     * 按邮箱查询用户。
     *
     * @param email 邮箱地址
     * @return 用户实体，不存在时返回空
     */
    User findByEmail(@Param("email") String email);

    /**
     * 分页查询后台用户列表。
     * <p>
     * 该接口专供 SYSTEM_ADMIN 后台页面使用，因此默认返回全量用户基础资料，具体权限由控制层和安全链统一收口。
     */
    List<User> findPage(@Param("limit") int limit, @Param("offset") int offset);

    /**
     * 统计后台用户总数，保证用户页分页信息与列表结果一致。
     */
    long countAll();

    /**
     * 查询带角色名称的当前用户资料。
     *
     * @param userId 用户 ID
     * @return 带角色名称的用户实体视图
     */
    User selectCurrentUserProfile(@Param("userId") String userId);
}
