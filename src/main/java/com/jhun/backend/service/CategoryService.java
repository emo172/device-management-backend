package com.jhun.backend.service;

import com.jhun.backend.dto.category.CategoryTreeResponse;
import com.jhun.backend.dto.category.CreateCategoryRequest;
import java.util.List;

/**
 * 设备分类服务。
 */
public interface CategoryService {

    CategoryTreeResponse createCategory(CreateCategoryRequest request);

    List<CategoryTreeResponse> getCategoryTree();
}
