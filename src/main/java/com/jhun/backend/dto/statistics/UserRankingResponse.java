package com.jhun.backend.dto.statistics;

/**
 * 用户借用排行榜响应。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param realName 真实姓名
 * @param totalBorrows 借出总数
 */
public record UserRankingResponse(String userId, String username, String realName, Integer totalBorrows) {
}
