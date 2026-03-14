package com.jhun.backend.service.support.reservation;

import com.jhun.backend.entity.Reservation;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 冲突检测器。
 * <p>
 * 用于识别同一设备在同一时间段内的预约重叠关系，为创建预约前的强校验提供统一逻辑。
 */
@Component
public class ConflictDetector {

    public boolean hasConflict(List<Reservation> reservations, LocalDateTime startTime, LocalDateTime endTime) {
        return reservations.stream().anyMatch(existing -> existing.getStartTime().isBefore(endTime)
                && existing.getEndTime().isAfter(startTime));
    }
}
