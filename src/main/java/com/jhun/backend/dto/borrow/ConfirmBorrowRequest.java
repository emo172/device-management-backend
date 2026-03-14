package com.jhun.backend.dto.borrow;

import java.time.LocalDateTime;

/**
 * 借用确认请求。
 *
 * @param borrowTime 实际借出时间；为空时默认使用当前时间，便于管理员现场快速确认
 * @param borrowCheckStatus 借出前设备检查记录；用于固化交接时的设备状态
 * @param remark 借出备注；用于补充现场说明
 */
public record ConfirmBorrowRequest(LocalDateTime borrowTime, String borrowCheckStatus, String remark) {
}
