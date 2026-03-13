package com.jhun.backend.dto.category;

/**
 * 创建设备分类请求。
 */
public record CreateCategoryRequest(
        String name,
        String parentName,
        Integer sortOrder,
        String description,
        String defaultApprovalMode) {
}
