package com.jhun.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * Prompt 模板实体。
 * <p>
 * 对应 SQL 真相源中的 {@code prompt_template} 表，承载系统管理员可维护的模板内容与启停状态，
 * 当前阶段主要为规则降级 provider 提供可配置模板来源，而不是直接接入真实 LLM 配置中心。
 */
@TableName("prompt_template")
public class PromptTemplate {

    /** 模板主键，统一使用 String UUID。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 模板名称，供后台运维人员识别。 */
    private String name;

    /** 模板代码，供程序稳定引用。 */
    private String code;

    /** 模板正文。 */
    private String content;

    /** 模板类型，仅允许四种真相源枚举。 */
    private String type;

    /** 模板说明。 */
    private String description;

    /** 模板变量说明 JSON。 */
    private String variables;

    /** 是否启用，1 表示启用，0 表示禁用。 */
    @TableField("is_active")
    private Integer isActive;

    /** 版本号，便于模板迭代管理。 */
    private String version;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVariables() {
        return variables;
    }

    public void setVariables(String variables) {
        this.variables = variables;
    }

    public Integer getIsActive() {
        return isActive;
    }

    public void setIsActive(Integer isActive) {
        this.isActive = isActive;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
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
