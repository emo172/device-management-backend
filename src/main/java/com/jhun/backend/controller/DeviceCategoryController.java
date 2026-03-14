package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.dto.category.CategoryTreeResponse;
import com.jhun.backend.dto.category.CreateCategoryRequest;
import com.jhun.backend.service.CategoryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备分类控制器。
 */
@RestController
@RequestMapping("/api/device-categories")
public class DeviceCategoryController {

    private final CategoryService categoryService;

    public DeviceCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public Result<CategoryTreeResponse> create(@RequestBody CreateCategoryRequest request) {
        return Result.success(categoryService.createCategory(request));
    }

    @GetMapping("/tree")
    public Result<List<CategoryTreeResponse>> tree() {
        return Result.success(categoryService.getCategoryTree());
    }
}
