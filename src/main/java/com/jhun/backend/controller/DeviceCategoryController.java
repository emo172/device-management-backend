package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.dto.category.CategoryTreeResponse;
import com.jhun.backend.dto.category.CreateCategoryRequest;
import com.jhun.backend.service.CategoryService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备分类控制器。
 * <p>
 * 分类树查询对所有已登录角色开放，但分类写入口必须收敛到 DEVICE_ADMIN，
 * 避免普通用户或系统管理员绕过设备管理职责边界直接改动分类体系。
 */
@RestController
@RequestMapping("/api/device-categories")
public class DeviceCategoryController {

    private final CategoryService categoryService;

    public DeviceCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * 创建设备分类。
     * <p>
     * 分类会直接影响设备建档与后续审批模式默认值，因此只允许 DEVICE_ADMIN 写入，
     * 防止非设备管理角色越权调整分类口径。
     */
    @PostMapping
    @PreAuthorize("hasRole('DEVICE_ADMIN')")
    public Result<CategoryTreeResponse> create(@RequestBody CreateCategoryRequest request) {
        return Result.success(categoryService.createCategory(request));
    }

    @GetMapping("/tree")
    public Result<List<CategoryTreeResponse>> tree() {
        return Result.success(categoryService.getCategoryTree());
    }
}
