package com.jhun.backend.service;

import com.jhun.backend.dto.user.FreezeUserRequest;
import com.jhun.backend.dto.user.UpdateUserRoleRequest;
import com.jhun.backend.dto.user.UpdateUserStatusRequest;
import com.jhun.backend.dto.user.UserAdminResponse;

/**
 * 用户管理服务。
 */
public interface UserService {

    UserAdminResponse updateUserStatus(String userId, UpdateUserStatusRequest request);

    UserAdminResponse freezeUser(String userId, FreezeUserRequest request);

    UserAdminResponse updateUserRole(String userId, UpdateUserRoleRequest request);
}
