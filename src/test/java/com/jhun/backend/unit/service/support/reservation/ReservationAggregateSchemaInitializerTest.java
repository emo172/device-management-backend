package com.jhun.backend.unit.service.support.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import com.jhun.backend.service.support.reservation.ReservationAggregateSchemaInitializer;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 预约聚合启动期修表测试。
 * <p>
 * 该测试直接模拟升级前旧库结构，验证启动器会在运行时自动补齐：
 * 1) 新增 `reservation_device`；
 * 2) 放宽 `reservation.device_id` 为可空；
 * 3) 把 `borrow_record` 唯一约束升级到 `(reservation_id, device_id)`。
 */
class ReservationAggregateSchemaInitializerTest {

    /**
     * 旧库升级后应允许同一预约下写入多条不同设备的 borrow_record。
     */
    @Test
    void upgradesLegacySchemaToReservationAggregateModel() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:reservation-aggregate-migration-test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createLegacySchema(jdbcTemplate);

        ReservationAggregateSchemaInitializer initializer =
                new ReservationAggregateSchemaInitializer(jdbcTemplate, dataSource);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        assertTrue(tableExists(dataSource, "reservation_device"));
        assertTrue(columnNullable(dataSource, "reservation", "device_id"));

        jdbcTemplate.update("INSERT INTO device (id) VALUES (?)", "device-1");
        jdbcTemplate.update("INSERT INTO device (id) VALUES (?)", "device-2");
        jdbcTemplate.update("INSERT INTO reservation (id, device_id) VALUES (?, ?)", "reservation-1", null);
        jdbcTemplate.update(
                "INSERT INTO reservation_device (id, reservation_id, device_id, device_order) VALUES (?, ?, ?, ?)",
                "relation-1",
                "reservation-1",
                "device-1",
                0);
        jdbcTemplate.update(
                "INSERT INTO reservation_device (id, reservation_id, device_id, device_order) VALUES (?, ?, ?, ?)",
                "relation-2",
                "reservation-1",
                "device-2",
                1);
        jdbcTemplate.update(
                "INSERT INTO borrow_record (id, reservation_id, device_id) VALUES (?, ?, ?)",
                "borrow-1",
                "reservation-1",
                "device-1");
        jdbcTemplate.update(
                "INSERT INTO borrow_record (id, reservation_id, device_id) VALUES (?, ?, ?)",
                "borrow-2",
                "reservation-1",
                "device-2");

        Long relationCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM reservation_device", Long.class);
        Long borrowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM borrow_record WHERE reservation_id = ?",
                Long.class,
                "reservation-1");
        assertEquals(2L, relationCount == null ? 0L : relationCount);
        assertEquals(2L, borrowCount == null ? 0L : borrowCount);
        assertFalse(hasUniqueIndex(dataSource, "borrow_record", "reservation_id"));
        assertTrue(hasUniqueIndex(dataSource, "borrow_record", "reservation_id", "device_id"));
    }

    private void createLegacySchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("CREATE TABLE device (id VARCHAR(36) NOT NULL PRIMARY KEY)");
        jdbcTemplate.execute("""
                CREATE TABLE reservation (
                    id VARCHAR(36) NOT NULL PRIMARY KEY,
                    device_id VARCHAR(36) NOT NULL,
                    CONSTRAINT fk_reservation_device_id FOREIGN KEY (device_id) REFERENCES device (id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE borrow_record (
                    id VARCHAR(36) NOT NULL PRIMARY KEY,
                    reservation_id VARCHAR(36) NOT NULL,
                    device_id VARCHAR(36) NOT NULL,
                    CONSTRAINT fk_borrow_reservation_id FOREIGN KEY (reservation_id) REFERENCES reservation (id),
                    CONSTRAINT fk_borrow_device_id FOREIGN KEY (device_id) REFERENCES device (id)
                )
                """);
        jdbcTemplate.execute("CREATE UNIQUE INDEX uk_borrow_reservation_id ON borrow_record (reservation_id)");
    }

    private boolean tableExists(DataSource dataSource, String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getTables(null, null, tableName.toUpperCase(Locale.ROOT), new String[]{"TABLE"})) {
                return resultSet.next();
            }
        }
    }

    private boolean columnNullable(DataSource dataSource, String tableName, String columnName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getColumns(
                    null,
                    null,
                    tableName.toUpperCase(Locale.ROOT),
                    columnName.toUpperCase(Locale.ROOT))) {
                if (!resultSet.next()) {
                    return false;
                }
                return resultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
            }
        }
    }

    private boolean hasUniqueIndex(DataSource dataSource, String tableName, String... expectedColumns) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getIndexInfo(null, null, tableName.toUpperCase(Locale.ROOT), true, false)) {
                List<String> expected = new ArrayList<>(expectedColumns.length);
                for (String expectedColumn : expectedColumns) {
                    expected.add(expectedColumn.toUpperCase(Locale.ROOT));
                }

                String currentIndex = null;
                List<String> currentColumns = new ArrayList<>();
                while (resultSet.next()) {
                    String indexName = resultSet.getString("INDEX_NAME");
                    String columnName = resultSet.getString("COLUMN_NAME");
                    if (indexName == null || columnName == null) {
                        continue;
                    }
                    if (!indexName.equals(currentIndex)) {
                        if (currentColumns.equals(expected)) {
                            return true;
                        }
                        currentIndex = indexName;
                        currentColumns = new ArrayList<>();
                    }
                    currentColumns.add(columnName.toUpperCase(Locale.ROOT));
                }
                return currentColumns.equals(expected);
            }
        }
    }
}
