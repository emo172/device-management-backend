package com.jhun.backend.common.enums;

/**
 * AI 执行结果枚举。
 * <p>
 * 当前 AI 能力只允许 `SUCCESS`、`FAILED`、`PENDING` 三种结果，
 * 统一收敛后可以避免服务实现、历史断言和测试数据继续依赖字符串字面量。
 */
public enum AiExecuteResult {

    /** 已成功完成本轮处理。 */
    SUCCESS,

    /** 当前无法识别或处理。 */
    FAILED,

    /** 已识别诉求但需要后续正式业务流程继续处理。 */
    PENDING
}
