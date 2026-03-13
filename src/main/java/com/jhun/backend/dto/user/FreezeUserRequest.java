package com.jhun.backend.dto.user;

/**
 * 冻结用户请求。
 *
 * @param freezeStatus 冻结状态：NORMAL、RESTRICTED、FROZEN
 * @param reason 冻结原因
 */
public record FreezeUserRequest(String freezeStatus, String reason) {
}
