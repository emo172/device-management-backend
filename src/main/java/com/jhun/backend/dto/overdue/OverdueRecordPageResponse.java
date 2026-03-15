package com.jhun.backend.dto.overdue;

import java.util.List;

/**
 * 逾期记录分页响应。
 *
 * @param total 满足筛选条件的总记录数
 * @param records 当前页记录
 */
public record OverdueRecordPageResponse(long total, List<OverdueRecordResponse> records) {
}
