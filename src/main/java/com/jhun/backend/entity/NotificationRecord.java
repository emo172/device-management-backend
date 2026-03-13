package com.jhun.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 通知记录实体。
 * <p>
 * 对应 SQL 真相源中的 {@code notification_record} 表，当前阶段优先支持站内信查询、未读统计与已读更新，
 * 同时保留邮件、短信渠道和关联业务字段，避免后续通知联动再调整表结构口径。
 */
@TableName("notification_record")
public class NotificationRecord {

    /** 通知主键，统一使用 String UUID。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 接收用户 ID。 */
    @TableField("user_id")
    private String userId;

    /** 通知类型。 */
    @TableField("notification_type")
    private String notificationType;

    /** 通知渠道：IN_APP、EMAIL、SMS。 */
    private String channel;

    /** 通知标题。 */
    private String title;

    /** 通知内容。 */
    private String content;

    /** 模板变量 JSON 文本。 */
    @TableField("template_vars")
    private String templateVars;

    /** 发送状态。 */
    private String status;

    /** 重试次数。 */
    @TableField("retry_count")
    private Integer retryCount;

    /** 错误信息。 */
    @TableField("error_message")
    private String errorMessage;

    /** 实际发送时间。 */
    @TableField("sent_at")
    private LocalDateTime sentAt;

    /** 已读标记，仅 IN_APP 生效。 */
    @TableField("read_flag")
    private Integer readFlag;

    /** 已读时间。 */
    @TableField("read_at")
    private LocalDateTime readAt;

    /** 关联业务 ID。 */
    @TableField("related_id")
    private String relatedId;

    /** 关联业务类型。 */
    @TableField("related_type")
    private String relatedType;

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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTemplateVars() {
        return templateVars;
    }

    public void setTemplateVars(String templateVars) {
        this.templateVars = templateVars;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public Integer getReadFlag() {
        return readFlag;
    }

    public void setReadFlag(Integer readFlag) {
        this.readFlag = readFlag;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    public String getRelatedId() {
        return relatedId;
    }

    public void setRelatedId(String relatedId) {
        this.relatedId = relatedId;
    }

    public String getRelatedType() {
        return relatedType;
    }

    public void setRelatedType(String relatedType) {
        this.relatedType = relatedType;
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
