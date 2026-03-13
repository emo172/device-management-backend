package com.jhun.backend.service.impl;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.user.FreezeUserRequest;
import com.jhun.backend.dto.user.UpdateUserRoleRequest;
import com.jhun.backend.dto.user.UpdateUserStatusRequest;
import com.jhun.backend.dto.user.UserAdminResponse;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户管理服务实现。
 * <p>
 * 当前阶段先覆盖账户状态、冻结状态与角色调整三类后台管理动作，为系统管理员运维入口提供最小闭环。
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    public UserServiceImpl(UserMapper userMapper, RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    @Transactional
    public UserAdminResponse updateUserStatus(String userId, UpdateUserStatusRequest request) {
        User user = mustFindUser(userId);
        user.setStatus(request.status());
        user.setFreezeReason(request.reason());
        userMapper.updateById(user);
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserAdminResponse freezeUser(String userId, FreezeUserRequest request) {
        User user = mustFindUser(userId);
        user.setFreezeStatus(request.freezeStatus());
        user.setFreezeReason(request.reason());
        userMapper.updateById(user);
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserAdminResponse updateUserRole(String userId, UpdateUserRoleRequest request) {
        User user = mustFindUser(userId);
        Role role = roleMapper.selectById(request.roleId());
        if (role == null) {
            throw new BusinessException("目标角色不存在");
        }
        user.setRoleId(role.getId());
        userMapper.updateById(user);
        return toResponse(user);
    }

    private User mustFindUser(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private UserAdminResponse toResponse(User user) {
        return new UserAdminResponse(user.getId(), user.getUsername(), user.getStatus(), user.getFreezeStatus(), user.getRoleId());
    }
}
