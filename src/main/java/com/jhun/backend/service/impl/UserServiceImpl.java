package com.jhun.backend.service.impl;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.user.FreezeUserRequest;
import com.jhun.backend.dto.user.UserDetailResponse;
import com.jhun.backend.dto.user.UserListItemResponse;
import com.jhun.backend.dto.user.UserPageResponse;
import com.jhun.backend.dto.user.UpdateUserRoleRequest;
import com.jhun.backend.dto.user.UpdateUserStatusRequest;
import com.jhun.backend.dto.user.UserAdminResponse;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.UserService;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户管理服务实现。
 * <p>
 * 当前阶段覆盖后台用户列表、详情、账户状态、冻结状态与角色调整等能力，
 * 为 SYSTEM_ADMIN 的用户管理页提供查询与运维闭环。
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
    /**
     * 查询后台用户列表。
     * <p>
     * 该列表服务于 SYSTEM_ADMIN 的用户管理页，因此需要稳定返回角色编码名和冻结状态等基础信息；
     * 角色信息在这里采用批量查询后 Map 组装，避免逐条查 role 表形成 N+1 查询。
     */
    public UserPageResponse listUsers(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;
        long total = userMapper.countAll();
        var users = userMapper.findPage(safeSize, offset);
        Map<String, Role> roleMap = loadRoleMap(users.stream().map(User::getRoleId).filter(Objects::nonNull).toList());
        var records = users.stream()
                .map(user -> toListItemResponse(user, roleMap))
                .toList();
        return new UserPageResponse(total, records);
    }

    @Override
    /**
     * 查询后台用户详情。
     * <p>
     * 详情接口用于查看角色、冻结原因与最近登录时间等运维字段，因此这里在服务层统一补齐角色编码名。
     */
    public UserDetailResponse getUserDetail(String userId) {
        return toDetailResponse(mustFindUser(userId));
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

    /**
     * 组装用户列表项。
     * <p>
     * 列表页直接依赖角色编码名展示三角色边界，因此这里统一通过 role_id 反查角色，避免控制层自行拼接。
     */
    private UserListItemResponse toListItemResponse(User user, Map<String, Role> roleMap) {
        Role role = mustFindMappedRole(roleMap, user.getRoleId());
        return new UserListItemResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRealName(),
                user.getPhone(),
                user.getStatus(),
                user.getFreezeStatus(),
                user.getRoleId(),
                role.getName());
    }

    /**
     * 组装用户详情。
     * <p>
     * 详情页除了列表基础字段外，还要补齐冻结原因、限制到期时间和最近登录时间，便于管理员做冻结/解冻判断。
     */
    private UserDetailResponse toDetailResponse(User user) {
        Role role = mustFindRole(user.getRoleId());
        return new UserDetailResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRealName(),
                user.getPhone(),
                user.getStatus(),
                user.getFreezeStatus(),
                user.getFreezeReason(),
                user.getFreezeExpireTime(),
                user.getRoleId(),
                role.getName(),
                user.getLastLoginTime(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    /**
     * 统一校验角色存在性。
     * <p>
     * 用户详情和列表都依赖固定三角色口径展示，如果 role_id 指向空角色，说明基础数据已损坏，需要及时暴露异常。
     */
    private Role mustFindRole(String roleId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("用户角色不存在");
        }
        return role;
    }

    /**
     * 批量加载角色映射。
     * <p>
     * 用户列表按页返回时，如果每条记录都单独查询角色，会造成明显的 N+1 查询放大；
     * 因此这里统一先批量拉取角色，再由列表 DTO 共享同一份映射。
     */
    private Map<String, Role> loadRoleMap(Iterable<String> roleIds) {
        Map<String, Role> roleMap = new HashMap<>();
        java.util.List<String> uniqueIds = new java.util.ArrayList<>();
        for (String roleId : roleIds) {
            if (roleId != null && !uniqueIds.contains(roleId)) {
                uniqueIds.add(roleId);
            }
        }
        if (uniqueIds.isEmpty()) {
            return roleMap;
        }
        roleMapper.selectByIds(uniqueIds).forEach(role -> roleMap.put(role.getId(), role));
        return roleMap;
    }

    /**
     * 从批量角色映射中读取角色。
     * <p>
     * 若角色映射缺失，说明数据已不满足固定三角色约束，需要显式失败而不是输出残缺列表数据。
     */
    private Role mustFindMappedRole(Map<String, Role> roleMap, String roleId) {
        Role role = roleMap.get(roleId);
        if (role == null) {
            throw new BusinessException("用户角色不存在");
        }
        return role;
    }

    private UserAdminResponse toResponse(User user) {
        return new UserAdminResponse(user.getId(), user.getUsername(), user.getStatus(), user.getFreezeStatus(), user.getRoleId());
    }
}
