package com.jhun.backend.dto.overdue;

import java.math.BigDecimal;

/**
 * 逾期处理请求。
 * <p>
 * 逾期处理属于设备管理员的业务职责，当前阶段允许管理员写入处理方式、备注与赔偿金额，
 * 用于闭合“逾期识别 -> 人工处理 -> 列表回显”的最小链路。
 *
 * @param processingMethod 处理方式：WARNING / COMPENSATION / CONTINUE
 * @param remark 处理备注
 * @param compensationAmount 赔偿金额，未赔偿时传 0 或空
 */
public record ProcessOverdueRequest(String processingMethod, String remark, BigDecimal compensationAmount) {
}
