package com.jhun.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jhun.backend.dto.borrow.BorrowRecordPageResponse;
import com.jhun.backend.entity.BorrowRecord;
import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.BorrowRecordMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.DeviceStatusLogMapper;
import com.jhun.backend.mapper.ReservationMapper;
import com.jhun.backend.mapper.UserMapper;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BorrowServiceImplTest {

    @Mock
    private BorrowRecordMapper borrowRecordMapper;

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private DeviceStatusLogMapper deviceStatusLogMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private BorrowServiceImpl borrowService;

    /**
     * 借还列表补展示字段后，页级查询仍必须保持批量预加载，不能退化回逐条 `selectById`。
     * 该测试直接保护 review 里提到的 `1 + 2N` 查询回归点。
     */
    @Test
    void shouldBatchLoadDevicesAndUsersWhenListingBorrowRecords() {
        BorrowRecord firstRecord = buildBorrowRecord("borrow-1", "reservation-1", "device-1", "user-1");
        BorrowRecord secondRecord = buildBorrowRecord("borrow-2", "reservation-2", "device-2", "user-2");
        Device firstDevice = buildDevice("device-1", "热成像仪", "DEV-001");
        Device secondDevice = buildDevice("device-2", "示波器", "DEV-002");
        User firstUser = buildUser("user-1", "张三", "zhangsan");
        User secondUser = buildUser("user-2", "", "lisi");

        when(borrowRecordMapper.countByConditions("BORROWED", null)).thenReturn(2L);
        when(borrowRecordMapper.findPageByConditions("BORROWED", null, 10, 0))
                .thenReturn(List.of(firstRecord, secondRecord));
        when(deviceMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(firstDevice, secondDevice));
        when(userMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(firstUser, secondUser));

        BorrowRecordPageResponse response = borrowService.listBorrowRecords("device-admin-1", "DEVICE_ADMIN", 1, 10, "BORROWED");

        assertEquals(2L, response.total());
        assertEquals(2, response.records().size());
        assertEquals("热成像仪", response.records().get(0).deviceName());
        assertEquals("张三", response.records().get(0).userName());
        assertEquals("lisi", response.records().get(1).userName());

        verify(deviceMapper).selectBatchIds(argThat((Collection<? extends Serializable> ids) ->
                ids.size() == 2 && ids.containsAll(List.of("device-1", "device-2"))));
        verify(userMapper).selectBatchIds(argThat((Collection<? extends Serializable> ids) ->
                ids.size() == 2 && ids.containsAll(List.of("user-1", "user-2"))));
        verify(deviceMapper, never()).selectById(any());
        verify(userMapper, never()).selectById(any());
    }

    private BorrowRecord buildBorrowRecord(String id, String reservationId, String deviceId, String userId) {
        BorrowRecord borrowRecord = new BorrowRecord();
        borrowRecord.setId(id);
        borrowRecord.setReservationId(reservationId);
        borrowRecord.setDeviceId(deviceId);
        borrowRecord.setUserId(userId);
        borrowRecord.setBorrowTime(LocalDateTime.parse("2026-03-20T09:00:00"));
        borrowRecord.setExpectedReturnTime(LocalDateTime.parse("2026-03-20T11:00:00"));
        borrowRecord.setStatus("BORROWED");
        return borrowRecord;
    }

    private Device buildDevice(String id, String name, String deviceNumber) {
        Device device = new Device();
        device.setId(id);
        device.setName(name);
        device.setDeviceNumber(deviceNumber);
        return device;
    }

    private User buildUser(String id, String realName, String username) {
        User user = new User();
        user.setId(id);
        user.setRealName(realName);
        user.setUsername(username);
        return user;
    }
}
