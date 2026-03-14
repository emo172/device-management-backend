package com.jhun.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 对话历史实体。
 * <p>
 * 对应 SQL 真相源中的 {@code chat_history} 表，负责沉淀用户输入、规则识别结果、结构化信息和执行状态，
 * 供 AI 历史页与后续分析能力复用；该表只记录 AI 过程，不得被用于绕过正式业务表的状态流转。
 */
@TableName("chat_history")
public class ChatHistory {

    /** 对话记录主键，统一使用 String UUID。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 发起本轮对话的用户 ID。 */
    @TableField("user_id")
    private String userId;

    /** 会话 ID，用于把同一轮多次交互串联到一起。 */
    @TableField("session_id")
    private String sessionId;

    /** 用户原始输入文本。 */
    @TableField("user_input")
    private String userInput;

    /** AI 返回给前端的最终文本。 */
    @TableField("ai_response")
    private String aiResponse;

    /** 识别到的固定意图，仅允许 RESERVE/QUERY/CANCEL/HELP/UNKNOWN。 */
    private String intent;

    /** 规则识别出的置信度。 */
    @TableField("intent_confidence")
    private BigDecimal intentConfidence;

    /** 从文本中提取出的结构化信息 JSON。 */
    @TableField("extracted_info")
    private String extractedInfo;

    /** 对话中识别出的设备 ID，当前阶段允许为空。 */
    @TableField("device_id")
    private String deviceId;

    /** 对话中识别出的预约 ID，当前阶段允许为空。 */
    @TableField("reservation_id")
    private String reservationId;

    /** 执行结果，仅允许 SUCCESS/FAILED/PENDING。 */
    @TableField("execute_result")
    private String executeResult;

    /** 规则降级或业务执行失败时的错误信息。 */
    @TableField("error_message")
    private String errorMessage;

    /** 当前响应由哪个模型或 provider 产出。 */
    @TableField("llm_model")
    private String llmModel;

    /** 响应耗时，单位毫秒。 */
    @TableField("response_time_ms")
    private Integer responseTimeMs;

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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserInput() {
        return userInput;
    }

    public void setUserInput(String userInput) {
        this.userInput = userInput;
    }

    public String getAiResponse() {
        return aiResponse;
    }

    public void setAiResponse(String aiResponse) {
        this.aiResponse = aiResponse;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public BigDecimal getIntentConfidence() {
        return intentConfidence;
    }

    public void setIntentConfidence(BigDecimal intentConfidence) {
        this.intentConfidence = intentConfidence;
    }

    public String getExtractedInfo() {
        return extractedInfo;
    }

    public void setExtractedInfo(String extractedInfo) {
        this.extractedInfo = extractedInfo;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public String getExecuteResult() {
        return executeResult;
    }

    public void setExecuteResult(String executeResult) {
        this.executeResult = executeResult;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public Integer getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(Integer responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
