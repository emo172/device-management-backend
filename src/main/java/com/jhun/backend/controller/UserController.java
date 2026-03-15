package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.dto.user.FreezeUserRequest;
import com.jhun.backend.dto.user.UserDetailResponse;
import com.jhun.backend.dto.user.UserPageResponse;
import com.jhun.backend.dto.user.UpdateUserRoleRequest;
import com.jhun.backend.dto.user.UpdateUserStatusRequest;
import com.jhun.backend.dto.user.UserAdminResponse;
import com.jhun.backend.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理控制器。
 * <p>
 * 系统管理员通过该控制器执行用户状态、角色与冻结状态管理，设备管理员和普通用户无权访问这些接口。
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户列表接口。
     * <p>
     * 该接口仅面向 SYSTEM_ADMIN，返回后台用户页所需的基础字段，避免设备管理员或普通用户获取全量账号清单。
     */
    @GetMapping
    public Result<UserPageResponse> list(
            @org.springframework.web.bind.annotation.RequestParam(value = "page", defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(value = "size", defaultValue = "10") int size) {
        return Result.success(userService.listUsers(page, size));
    }

    /**
     * 用户详情接口。
     * <p>
     * 用户详情用于后台查看单个账号的角色、冻结原因与最近登录时间等运维字段，继续复用 SYSTEM_ADMIN 的统一权限边界。
     */
    @GetMapping("/{id}")
    public Result<UserDetailResponse> detail(@PathVariable("id") String userId) {
        return Result.success(userService.getUserDetail(userId));
    }

    @PutMapping("/{id}/status")
    public Result<UserAdminResponse> updateStatus(@PathVariable("id") String userId, @RequestBody UpdateUserStatusRequest request) {
        return Result.success(userService.updateUserStatus(userId, request));
    }

    @PutMapping("/{id}/role")
    public Result<UserAdminResponse> updateRole(@PathVariable("id") String userId, @RequestBody UpdateUserRoleRequest request) {
        return Result.success(userService.updateUserRole(userId, request));
    }

    @PostMapping("/{id}/freeze")
    public Result<UserAdminResponse> freeze(@PathVariable("id") String userId, @RequestBody FreezeUserRequest request) {
        return Result.success(userService.freezeUser(userId, request));
    }
}
