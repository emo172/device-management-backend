package com.jhun.backend.dto.borrow;

import java.util.List;

/**
 * 借还记录分页响应。
 *
 * @param total 满足筛选条件的总记录数
 * @param records 当前页记录
 */
public record BorrowRecordPageResponse(long total, List<BorrowRecordResponse> records) {
}
