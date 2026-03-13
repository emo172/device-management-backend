package com.jhun.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 用户实体。
 * <p>
 * 对应 SQL 真相源中的 {@code user} 表，当前阶段承载认证、个人资料与冻结状态基线；
 * 主键、角色、冻结状态等字段必须保持与真相源一致，不能退回到 Long ID 或旧两角色口径。
 */
@TableName("`user`")
public class User {

    /** 用户主键，统一使用 String UUID。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 用户名，支持作为登录账号。 */
    private String username;

    /** 邮箱地址，同样支持作为登录账号。 */
    private String email;

    /** BCrypt 哈希后的密码。 */
    @TableField("password_hash")
    private String passwordHash;

    /** 关联角色 ID。 */
    @TableField("role_id")
    private String roleId;

    /** 真实姓名。 */
    @TableField("real_name")
    private String realName;

    /** 手机号。 */
    private String phone;

    /** 账户状态：1 正常，0 禁用。 */
    private Integer status;

    /** 冻结状态：NORMAL、RESTRICTED、FROZEN。 */
    @TableField("freeze_status")
    private String freezeStatus;

    /** 冻结或限制原因。 */
    @TableField("freeze_reason")
    private String freezeReason;

    /** 限制到期时间。 */
    @TableField("freeze_expire_time")
    private LocalDateTime freezeExpireTime;

    /** 最近登录时间。 */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /** 创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getFreezeStatus() {
        return freezeStatus;
    }

    public void setFreezeStatus(String freezeStatus) {
        this.freezeStatus = freezeStatus;
    }

    public String getFreezeReason() {
        return freezeReason;
    }

    public void setFreezeReason(String freezeReason) {
        this.freezeReason = freezeReason;
    }

    public LocalDateTime getFreezeExpireTime() {
        return freezeExpireTime;
    }

    public void setFreezeExpireTime(LocalDateTime freezeExpireTime) {
        this.freezeExpireTime = freezeExpireTime;
    }

    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(LocalDateTime lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
