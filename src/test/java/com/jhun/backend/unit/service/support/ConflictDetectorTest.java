package com.jhun.backend.unit.service.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jhun.backend.entity.Reservation;
import com.jhun.backend.service.support.reservation.ConflictDetector;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 冲突检测器测试。
 * <p>
 * 用于验证设备同时间段预约冲突识别，防止后续并发预约时出现重叠记录。
 */
class ConflictDetectorTest {

    private final ConflictDetector conflictDetector = new ConflictDetector();

    /**
     * 验证重叠时间段会被识别为冲突。
     */
    @Test
    void shouldDetectTimeOverlapConflict() {
        Reservation existing = new Reservation();
        existing.setStartTime(LocalDateTime.of(2026, 3, 16, 9, 0));
        existing.setEndTime(LocalDateTime.of(2026, 3, 16, 11, 0));

        boolean conflict = conflictDetector.hasConflict(
                List.of(existing),
                LocalDateTime.of(2026, 3, 16, 10, 0),
                LocalDateTime.of(2026, 3, 16, 12, 0));

        assertTrue(conflict);
    }

    /**
     * 验证相邻但不重叠时间段不应判定为冲突。
     */
    @Test
    void shouldAllowAdjacentReservationWithoutConflict() {
        Reservation existing = new Reservation();
        existing.setStartTime(LocalDateTime.of(2026, 3, 16, 9, 0));
        existing.setEndTime(LocalDateTime.of(2026, 3, 16, 11, 0));

        boolean conflict = conflictDetector.hasConflict(
                List.of(existing),
                LocalDateTime.of(2026, 3, 16, 11, 0),
                LocalDateTime.of(2026, 3, 16, 12, 0));

        assertFalse(conflict);
    }
}
