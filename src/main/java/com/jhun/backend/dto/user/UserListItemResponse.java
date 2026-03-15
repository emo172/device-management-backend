package com.jhun.backend.dto.user;

/**
 * 用户列表单项响应。
 *
 * @param id 用户 ID
 * @param username 用户名
 * @param email 邮箱
 * @param realName 真实姓名
 * @param phone 手机号
 * @param status 账户状态
 * @param freezeStatus 冻结状态
 * @param roleId 角色 ID
 * @param roleName 角色编码名；后台列表直接依赖该值区分三角色
 */
public record UserListItemResponse(
        String id,
        String username,
        String email,
        String realName,
        String phone,
        Integer status,
        String freezeStatus,
        String roleId,
        String roleName) {
}
