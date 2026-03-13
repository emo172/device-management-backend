package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.dto.user.FreezeUserRequest;
import com.jhun.backend.dto.user.UpdateUserRoleRequest;
import com.jhun.backend.dto.user.UpdateUserStatusRequest;
import com.jhun.backend.dto.user.UserAdminResponse;
import com.jhun.backend.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
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
