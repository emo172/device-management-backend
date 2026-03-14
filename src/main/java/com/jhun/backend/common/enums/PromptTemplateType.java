package com.jhun.backend.common.enums;

/**
 * Prompt 模板类型枚举。
 * <p>
 * 该枚举集中维护 Prompt 模板四种正式类型，既用于后台校验，也用于 AI 规则降级组件读取模板，
 * 以保证模板配置和运行时代码始终共用同一份类型口径。
 */
public enum PromptTemplateType {

    /** 意图识别模板。 */
    INTENT_RECOGNITION,

    /** 信息提取模板。 */
    INFO_EXTRACTION,

    /** 结果反馈模板。 */
    RESULT_FEEDBACK,

    /** 冲突推荐模板。 */
    CONFLICT_RECOMMENDATION
}
