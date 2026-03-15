package com.jhun.backend.dto.user;

import java.time.LocalDateTime;

/**
 * 用户详情响应。
 *
 * @param id 用户 ID
 * @param username 用户名
 * @param email 邮箱
 * @param realName 真实姓名
 * @param phone 手机号
 * @param status 账户状态
 * @param freezeStatus 冻结状态
 * @param freezeReason 冻结或限制原因
 * @param freezeExpireTime 限制自动解除时间；仅 RESTRICTED 常用
 * @param roleId 角色 ID
 * @param roleName 角色编码名
 * @param lastLoginTime 最近登录时间
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record UserDetailResponse(
        String id,
        String username,
        String email,
        String realName,
        String phone,
        Integer status,
        String freezeStatus,
        String freezeReason,
        LocalDateTime freezeExpireTime,
        String roleId,
        String roleName,
        LocalDateTime lastLoginTime,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
