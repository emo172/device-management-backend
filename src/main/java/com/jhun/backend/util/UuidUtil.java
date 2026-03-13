package com.jhun.backend.util;

import java.util.UUID;

/**
 * UUID 工具类。
 * <p>
 * 数据库真相源明确要求所有主键统一使用 {@code VARCHAR(36)} / {@code String UUID}，
 * 因此通过该工具集中生成字符串主键，避免业务代码误回退到数字 ID 假设。
 */
public final class UuidUtil {

    private UuidUtil() {
    }

    /**
     * 生成标准 36 位 UUID 字符串。
     *
     * @return 符合数据库主键约束的 UUID 字符串
     */
    public static String randomUuid() {
        return UUID.randomUUID().toString();
    }
}
