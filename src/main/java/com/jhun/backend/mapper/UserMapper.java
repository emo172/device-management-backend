package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.User;
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
     * 查询带角色名称的当前用户资料。
     *
     * @param userId 用户 ID
     * @return 带角色名称的用户实体视图
     */
    User selectCurrentUserProfile(@Param("userId") String userId);
}
