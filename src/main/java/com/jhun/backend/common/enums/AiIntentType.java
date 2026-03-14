package com.jhun.backend.common.enums;

/**
 * AI 固定意图枚举。
 * <p>
 * Task 13 明确要求 AI 意图只能在五种固定口径内流转，所有生产代码与测试都应复用该枚举，
 * 避免 `RESERVE`、`QUERY` 等字符串散落在控制层、服务层和测试代码中导致口径漂移。
 */
public enum AiIntentType {

    /** 预约诉求。 */
    RESERVE,

    /** 查询诉求。 */
    QUERY,

    /** 取消诉求。 */
    CANCEL,

    /** 帮助诉求。 */
    HELP,

    /** 未识别诉求。 */
    UNKNOWN
}
