package com.jhun.backend.service.support.reservation;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 预约聚合模型增量建表/修表启动器。
 * <p>
 * 当前仓库尚未接入正式迁移框架，但运行时已经切到 `reservation_device` +
 * `borrow_record(reservation_id, device_id)` 的新真相模型。
 * 因此这里在应用启动早期以幂等方式补齐旧库缺失的表结构与约束，
 * 避免现网数据库仍停留在旧 schema 时直接因为表不存在、列仍然 NOT NULL 或唯一约束过旧而启动/写入失败。
 */
@Component
@Order(0)
public class ReservationAggregateSchemaInitializer implements ApplicationRunner {

    private static final String MYSQL = "mysql";
    private static final String H2 = "h2";
    private static final String RESERVATION_DEVICE_TABLE = "reservation_device";
    private static final String RESERVATION_TABLE = "reservation";
    private static final String BORROW_RECORD_TABLE = "borrow_record";
    private static final String LEGACY_BORROW_RECORD_UNIQUE_NAME = "uk_borrow_reservation_id";

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public ReservationAggregateSchemaInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        String databaseProduct = detectDatabaseProduct();
        ensureReservationDeviceTableExists();
        ensureReservationDeviceIdNullable(databaseProduct);
        ensureBorrowRecordCompositeUniqueConstraint(databaseProduct);
    }

    /**
     * 补齐 `reservation_device` 正式真相表。
     * <p>
     * 旧库没有这张表时，后续回填器和读路径都会直接失败；
     * 这里按最小必要字段与约束创建它，保证回填和多设备聚合可以继续执行。
     */
    private void ensureReservationDeviceTableExists() {
        if (tableExists(RESERVATION_DEVICE_TABLE)) {
            return;
        }
        jdbcTemplate.execute("""
                CREATE TABLE reservation_device (
                    id VARCHAR(36) NOT NULL PRIMARY KEY,
                    reservation_id VARCHAR(36) NOT NULL,
                    device_id VARCHAR(36) NOT NULL,
                    device_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_reservation_device_reservation_device UNIQUE (reservation_id, device_id),
                    CONSTRAINT uk_reservation_device_reservation_order UNIQUE (reservation_id, device_order),
                    CONSTRAINT fk_reservation_device_reservation_id FOREIGN KEY (reservation_id) REFERENCES reservation (id) ON DELETE CASCADE,
                    CONSTRAINT fk_reservation_device_device_id FOREIGN KEY (device_id) REFERENCES device (id)
                )
                """);
    }

    /**
     * 把 `reservation.device_id` 从旧的强制非空兼容列放宽为可空。
     * <p>
     * 新写路径已经不再把该列当成真相源持续写回；
     * 如果旧库仍保留 NOT NULL，新建预约会在主表 insert 阶段直接失败。
     */
    private void ensureReservationDeviceIdNullable(String databaseProduct) {
        if (!columnExists(RESERVATION_TABLE, "device_id") || columnNullable(RESERVATION_TABLE, "device_id")) {
            return;
        }
        if (MYSQL.equals(databaseProduct)) {
            jdbcTemplate.execute("ALTER TABLE reservation MODIFY COLUMN device_id VARCHAR(36) NULL");
            return;
        }
        jdbcTemplate.execute("ALTER TABLE reservation ALTER COLUMN device_id DROP NOT NULL");
    }

    /**
     * 把借还记录唯一约束从“同预约只能 1 条”升级为“同预约同设备只能 1 条”。
     * <p>
     * 多设备预约在借出时会为每台设备各写 1 条 borrow_record；
     * 若旧唯一约束仍停留在 `reservation_id` 单列，第二条记录一定会被数据库拒绝。
     */
    private void ensureBorrowRecordCompositeUniqueConstraint(String databaseProduct) {
        Map<String, List<String>> uniqueIndexes = loadUniqueIndexes(BORROW_RECORD_TABLE);
        if (containsIndexColumns(uniqueIndexes, "reservation_id", "device_id")) {
            return;
        }
        String legacySingleReservationIndex = findIndexNameByColumns(uniqueIndexes, "reservation_id");
        if (legacySingleReservationIndex == null && containsIndexNamePrefix(uniqueIndexes, LEGACY_BORROW_RECORD_UNIQUE_NAME)) {
            legacySingleReservationIndex = LEGACY_BORROW_RECORD_UNIQUE_NAME;
        }

        /*
         * MySQL 下旧单列唯一索引 `uk_borrow_reservation_id` 同时承担 `borrow_record.reservation_id`
         * 外键的前导索引角色；如果直接 DROP INDEX，会被 InnoDB 以“foreign key still needs this index”拒绝。
         * 因此必须先补上新的 `(reservation_id, device_id)` 复合唯一，让外键索引需求由新索引承接，
         * 然后再安全移除旧单列唯一。
         */
        jdbcTemplate.execute(
                "ALTER TABLE borrow_record ADD CONSTRAINT uk_borrow_reservation_device UNIQUE (reservation_id, device_id)");

        if (legacySingleReservationIndex != null) {
            dropUniqueConstraint(databaseProduct, BORROW_RECORD_TABLE, legacySingleReservationIndex);
        }
    }

    private void dropUniqueConstraint(String databaseProduct, String tableName, String constraintName) {
        if (MYSQL.equals(databaseProduct)) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " DROP INDEX " + constraintName);
            return;
        }
        if (H2.equals(databaseProduct)) {
            dropH2LegacyUniqueConstraint(tableName, constraintName);
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " DROP CONSTRAINT " + constraintName);
    }

    /**
     * H2 的旧唯一约束可能表现成独立索引，也可能表现成命名约束。
     * <p>
     * 因此这里先按索引删除；若失败，再回退到删除固定历史约束名，确保测试环境也能覆盖旧 schema 升级路径。
     */
    private void dropH2LegacyUniqueConstraint(String tableName, String constraintName) {
        try {
            jdbcTemplate.execute("DROP INDEX " + constraintName);
        } catch (DataAccessException exception) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " DROP CONSTRAINT " + LEGACY_BORROW_RECORD_UNIQUE_NAME);
        }
    }

    private String detectDatabaseProduct() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        } catch (SQLException exception) {
            throw new IllegalStateException("读取数据库产品信息失败，无法执行预约聚合增量修表", exception);
        }
    }

    private boolean tableExists(String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return hasTable(metaData, tableName);
        } catch (SQLException exception) {
            throw new IllegalStateException("检查数据表是否存在失败: " + tableName, exception);
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getColumns(null, null, tableName, columnName)) {
                if (resultSet.next()) {
                    return true;
                }
            }
            try (ResultSet resultSet = metaData.getColumns(null, null, tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT))) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("检查数据列是否存在失败: " + tableName + "." + columnName, exception);
        }
    }

    private boolean columnNullable(String tableName, String columnName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            Integer nullable = readColumnNullable(metaData, tableName, columnName);
            if (nullable == null) {
                throw new IllegalStateException("未找到数据列: " + tableName + "." + columnName);
            }
            return nullable == DatabaseMetaData.columnNullable;
        } catch (SQLException exception) {
            throw new IllegalStateException("读取数据列可空性失败: " + tableName + "." + columnName, exception);
        }
    }

    private Integer readColumnNullable(DatabaseMetaData metaData, String tableName, String columnName) throws SQLException {
        try (ResultSet resultSet = metaData.getColumns(null, null, tableName, columnName)) {
            if (resultSet.next()) {
                return resultSet.getInt("NULLABLE");
            }
        }
        try (ResultSet resultSet = metaData.getColumns(
                null,
                null,
                tableName.toUpperCase(Locale.ROOT),
                columnName.toUpperCase(Locale.ROOT))) {
            if (resultSet.next()) {
                return resultSet.getInt("NULLABLE");
            }
        }
        return null;
    }

    private Map<String, List<String>> loadUniqueIndexes(String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            Map<String, List<IndexedColumn>> indexedColumns = new HashMap<>();
            collectUniqueIndexes(metaData, tableName, indexedColumns);
            if (indexedColumns.isEmpty()) {
                collectUniqueIndexes(metaData, tableName.toUpperCase(Locale.ROOT), indexedColumns);
            }
            Map<String, List<String>> result = new HashMap<>();
            for (Map.Entry<String, List<IndexedColumn>> entry : indexedColumns.entrySet()) {
                List<String> columns = entry.getValue().stream()
                        .sorted(Comparator.comparingInt(IndexedColumn::ordinalPosition))
                        .map(IndexedColumn::columnName)
                        .map(columnName -> columnName.toLowerCase(Locale.ROOT))
                        .toList();
                result.put(entry.getKey(), columns);
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("读取唯一索引信息失败: " + tableName, exception);
        }
    }

    private void collectUniqueIndexes(
            DatabaseMetaData metaData,
            String tableName,
            Map<String, List<IndexedColumn>> indexedColumns) throws SQLException {
        try (ResultSet resultSet = metaData.getIndexInfo(null, null, tableName, true, false)) {
            while (resultSet.next()) {
                String indexName = resultSet.getString("INDEX_NAME");
                String columnName = resultSet.getString("COLUMN_NAME");
                if (indexName == null || columnName == null) {
                    continue;
                }
                short ordinalPosition = resultSet.getShort("ORDINAL_POSITION");
                indexedColumns.computeIfAbsent(indexName, key -> new ArrayList<>())
                        .add(new IndexedColumn(ordinalPosition, columnName));
            }
        }
    }

    private boolean containsIndexColumns(Map<String, List<String>> uniqueIndexes, String... expectedColumns) {
        List<String> normalizedExpected = normalizeColumns(expectedColumns);
        return uniqueIndexes.values().stream().anyMatch(columns -> columns.equals(normalizedExpected));
    }

    private String findIndexNameByColumns(Map<String, List<String>> uniqueIndexes, String... expectedColumns) {
        List<String> normalizedExpected = normalizeColumns(expectedColumns);
        return uniqueIndexes.entrySet().stream()
                .filter(entry -> entry.getValue().equals(normalizedExpected))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private boolean containsIndexNamePrefix(Map<String, List<String>> uniqueIndexes, String expectedPrefix) {
        String normalizedPrefix = expectedPrefix.toLowerCase(Locale.ROOT);
        return uniqueIndexes.keySet().stream()
                .map(indexName -> indexName.toLowerCase(Locale.ROOT))
                .anyMatch(indexName -> indexName.startsWith(normalizedPrefix));
    }

    private List<String> normalizeColumns(String... columns) {
        List<String> normalized = new ArrayList<>(columns.length);
        for (String column : columns) {
            normalized.add(column.toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    private boolean hasTable(DatabaseMetaData metaData, String tableName) throws SQLException {
        try (ResultSet resultSet = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getTables(
                null,
                null,
                tableName.toUpperCase(Locale.ROOT),
                new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }

    private record IndexedColumn(int ordinalPosition, String columnName) {
        private IndexedColumn {
            Objects.requireNonNull(columnName, "columnName");
        }
    }
}
