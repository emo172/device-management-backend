package com.jhun.backend.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.reservation.CancelReservationRequest;
import com.jhun.backend.entity.Reservation;
import com.jhun.backend.mapper.DeviceCategoryMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.NotificationRecordMapper;
import com.jhun.backend.mapper.ReservationMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.impl.ReservationServiceImpl;
import com.jhun.backend.service.support.reservation.ConflictDetector;
import com.jhun.backend.service.support.reservation.ReservationValidator;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 预约服务实现单元测试。
 * <p>
 * 该测试只锁定本轮审查修复引入的关键回归风险，避免取消接口再次退回“先查后改”的覆盖式更新。
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private DeviceCategoryMapper deviceCategoryMapper;

    @Mock
    private NotificationRecordMapper notificationRecordMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private ReservationValidator reservationValidator;

    @Mock
    private ConflictDetector conflictDetector;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private ReservationServiceImpl reservationService;

    /**
     * 验证当数据库里的预约状态已经在并发请求中变化时，取消接口会显式失败，
     * 而不是继续把旧快照覆盖写成 `CANCELLED`。
     */
    @Test
    void shouldFailCancelWhenDatabaseStateChangedConcurrently() {
        Reservation reservation = new Reservation();
        reservation.setId("reservation-1");
        reservation.setUserId("user-1");
        reservation.setStatus("APPROVED");
        reservation.setSignStatus("NOT_CHECKED_IN");
        reservation.setStartTime(LocalDateTime.now().plusDays(2));

        when(reservationMapper.selectById("reservation-1")).thenReturn(reservation);
        when(reservationMapper.cancelReservationSafely(anyString(), anyString(), any(), any(), any(), anyList()))
                .thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> reservationService.cancelReservation(
                        "reservation-1",
                        "user-1",
                        "USER",
                        new CancelReservationRequest("并发取消")));

        assertEquals("预约状态已变化，请刷新后重试", exception.getMessage());
        verifyNoInteractions(notificationRecordMapper);
    }
}
