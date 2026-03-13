package com.jhun.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 密码历史实体。
 * <p>
 * 用于阻止用户在修改密码或重置密码时复用历史密码，保护计划要求中的密码历史治理规则。
 */
@TableName("password_history")
public class PasswordHistory {

    /** 记录主键，统一使用 String UUID。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 用户 ID。 */
    @TableField("user_id")
    private String userId;

    /** 历史密码哈希。 */
    @TableField("password_hash")
    private String passwordHash;

    /** 创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
