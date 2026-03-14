package com.jhun.backend.service.impl;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.category.CategoryTreeResponse;
import com.jhun.backend.dto.category.CreateCategoryRequest;
import com.jhun.backend.entity.DeviceCategory;
import com.jhun.backend.mapper.DeviceCategoryMapper;
import com.jhun.backend.service.CategoryService;
import com.jhun.backend.util.UuidUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 设备分类服务实现。
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    private final DeviceCategoryMapper deviceCategoryMapper;

    public CategoryServiceImpl(DeviceCategoryMapper deviceCategoryMapper) {
        this.deviceCategoryMapper = deviceCategoryMapper;
    }

    @Override
    @Transactional
    public CategoryTreeResponse createCategory(CreateCategoryRequest request) {
        String parentId = null;
        if (request.parentName() != null && !request.parentName().isBlank()) {
            DeviceCategory parent = deviceCategoryMapper.findRootByName(request.parentName());
            if (parent == null) {
                throw new BusinessException("父分类不存在");
            }
            parentId = parent.getId();
        }
        if (deviceCategoryMapper.findByParentIdAndName(parentId, request.name()) != null) {
            throw new BusinessException("同级分类名称已存在");
        }
        DeviceCategory category = new DeviceCategory();
        category.setId(UuidUtil.randomUuid());
        category.setName(request.name());
        category.setParentId(parentId);
        category.setSortOrder(request.sortOrder());
        category.setDescription(request.description());
        category.setDefaultApprovalMode(request.defaultApprovalMode());
        deviceCategoryMapper.insert(category);
        return toTreeResponse(category, List.of());
    }

    @Override
    public List<CategoryTreeResponse> getCategoryTree() {
        List<DeviceCategory> categories = deviceCategoryMapper.findAllOrderBySort();
        Map<String, List<DeviceCategory>> childrenMap = categories.stream()
                .filter(category -> category.getParentId() != null)
                .collect(Collectors.groupingBy(DeviceCategory::getParentId));
        Map<String, DeviceCategory> categoryMap = categories.stream()
                .collect(Collectors.toMap(DeviceCategory::getId, Function.identity()));

        List<CategoryTreeResponse> roots = new ArrayList<>();
        for (DeviceCategory category : categories) {
            if (category.getParentId() == null) {
                roots.add(buildNode(category, childrenMap, categoryMap));
            }
        }
        return roots;
    }

    private CategoryTreeResponse buildNode(
            DeviceCategory category,
            Map<String, List<DeviceCategory>> childrenMap,
            Map<String, DeviceCategory> categoryMap) {
        List<CategoryTreeResponse> children = childrenMap.getOrDefault(category.getId(), List.of()).stream()
                .map(child -> buildNode(child, childrenMap, categoryMap))
                .toList();
        return toTreeResponse(category, children);
    }

    private CategoryTreeResponse toTreeResponse(DeviceCategory category, List<CategoryTreeResponse> children) {
        return new CategoryTreeResponse(
                category.getId(),
                category.getName(),
                category.getParentId(),
                category.getSortOrder(),
                category.getDescription(),
                category.getDefaultApprovalMode(),
                children);
    }
}
