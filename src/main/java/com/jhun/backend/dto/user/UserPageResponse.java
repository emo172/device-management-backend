package com.jhun.backend.dto.user;

import java.util.List;

/**
 * 用户分页响应。
 *
 * @param total 当前后台可见用户总数
 * @param records 当前页用户记录
 */
public record UserPageResponse(long total, List<UserListItemResponse> records) {
}
