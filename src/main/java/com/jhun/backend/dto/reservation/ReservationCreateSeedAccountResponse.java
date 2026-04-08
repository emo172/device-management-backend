package com.jhun.backend.dto.reservation;

/**
 * reservation-create internal seed 账号响应。
 * <p>
 * `password` 仅在当前账号允许被前端联调脚本直接消费时返回；
 * 对管理员账号默认返回 `null`，避免 internal seed 顺带成为明文凭据分发入口。
 */
public record ReservationCreateSeedAccountResponse(
        String userId,
        String username,
        String email,
        String loginAccount,
        String password,
        String role) {
}
