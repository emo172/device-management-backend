package com.jhun.backend.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jhun.backend.util.UuidUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

/**
 * 阶段 0 冒烟测试。
 * <p>
 * 用于确认基础 SQL 资产与 UUID 工具已随应用一起就绪，避免进入后续业务开发后才发现基线资源缺失。
 */
@SpringBootTest
@ActiveProfiles("test")
class Stage0SmokeIT {

    /**
     * 验证四份 SQL 基线脚本存在，且 UUID 工具输出符合 36 位字符串主键约束。
     */
    @Test
    void shouldExposeStage0BaselineAssets() {
        assertTrue(new ClassPathResource("sql/01_schema.sql").exists());
        assertTrue(new ClassPathResource("sql/02_seed_roles.sql").exists());
        assertTrue(new ClassPathResource("sql/03_seed_permissions.sql").exists());
        assertTrue(new ClassPathResource("sql/04_seed_role_permissions.sql").exists());
        assertEquals(36, UuidUtil.randomUuid().length());
    }
}
