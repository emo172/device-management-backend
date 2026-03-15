package com.jhun.backend.service;

import com.jhun.backend.dto.user.FreezeUserRequest;
import com.jhun.backend.dto.user.UserDetailResponse;
import com.jhun.backend.dto.user.UserPageResponse;
import com.jhun.backend.dto.user.UpdateUserRoleRequest;
import com.jhun.backend.dto.user.UpdateUserStatusRequest;
import com.jhun.backend.dto.user.UserAdminResponse;

/**
 * 用户管理服务。
 */
public interface UserService {

    /**
     * 查询后台用户列表。
     * <p>
     * 该接口服务于 SYSTEM_ADMIN 的用户管理页，需要返回角色与冻结状态等基础资料，并保持分页统计一致。
     */
    UserPageResponse listUsers(int page, int size);

    /**
     * 查询后台用户详情。
     * <p>
     * 详情接口用于系统管理员查看单个用户的角色、冻结信息与最近登录时间等运维信息。
     */
    UserDetailResponse getUserDetail(String userId);

    UserAdminResponse updateUserStatus(String userId, UpdateUserStatusRequest request);

    UserAdminResponse freezeUser(String userId, FreezeUserRequest request);

    UserAdminResponse updateUserRole(String userId, UpdateUserRoleRequest request);
}
