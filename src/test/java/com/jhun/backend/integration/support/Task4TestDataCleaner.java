package com.jhun.backend.integration.support;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Task 4 测试数据清理器。
 * <p>
 * 角色权限与用户管理测试会重复写入用户、角色权限关联等数据，使用该组件在每个测试前清空动态数据，
 * 避免测试间相互污染并保持断言稳定。
 */
@Component
public class Task4TestDataCleaner {

    private final JdbcTemplate jdbcTemplate;

    public Task4TestDataCleaner(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * 清理 Task 4 测试产生的动态数据，并保留角色和权限种子数据。
     */
    public void clean() {
        jdbcTemplate.update("DELETE FROM role_permission WHERE id NOT LIKE 'seed-%'");
        jdbcTemplate.update("DELETE FROM password_history");
        jdbcTemplate.update("DELETE FROM `user`");
    }
}
