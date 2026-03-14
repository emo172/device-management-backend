package com.jhun.backend.dto.borrow;

import java.time.LocalDateTime;

/**
 * 归还确认请求。
 *
 * @param returnTime 实际归还时间；为空时默认使用当前时间，便于管理员现场确认回收
 * @param returnCheckStatus 归还检查记录；用于保留设备回收时的状态审计信息
 * @param remark 归还备注；用于补充回收说明
 */
public record ConfirmReturnRequest(LocalDateTime returnTime, String returnCheckStatus, String remark) {
}
