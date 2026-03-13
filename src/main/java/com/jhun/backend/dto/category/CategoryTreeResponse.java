package com.jhun.backend.dto.category;

import java.util.List;

/**
 * 分类树响应。
 */
public record CategoryTreeResponse(
        String id,
        String name,
        String parentId,
        Integer sortOrder,
        String description,
        String defaultApprovalMode,
        List<CategoryTreeResponse> children) {
}
