package com.jhun.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jhun.backend.dto.overdue.OverdueRecordPageResponse;
import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.OverdueRecord;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.BorrowRecordMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.NotificationRecordMapper;
import com.jhun.backend.mapper.OverdueRecordMapper;
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
class OverdueServiceImplTest {

    @Mock
    private OverdueRecordMapper overdueRecordMapper;

    @Mock
    private BorrowRecordMapper borrowRecordMapper;

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private NotificationRecordMapper notificationRecordMapper;

    @InjectMocks
    private OverdueServiceImpl overdueService;

    /**
     * 逾期列表补用户名与设备名后，同样必须继续走页级批量预加载。
     * 这里同时校验设备、用户映射命中与“不再逐条 `selectById`”这两个关键约束。
     */
    @Test
    void shouldBatchLoadDevicesAndUsersWhenListingOverdueRecords() {
        OverdueRecord firstRecord = buildOverdueRecord("overdue-1", "borrow-1", "device-1", "user-1");
        OverdueRecord secondRecord = buildOverdueRecord("overdue-2", "borrow-2", "device-2", "user-2");
        Device firstDevice = buildDevice("device-1", "热成像仪", "DEV-001");
        Device secondDevice = buildDevice("device-2", "示波器", "DEV-002");
        User firstUser = buildUser("user-1", "张三", "zhangsan");
        User secondUser = buildUser("user-2", "", "lisi");

        when(overdueRecordMapper.countByConditions("PENDING", null)).thenReturn(2L);
        when(overdueRecordMapper.findPageByConditions("PENDING", null, 10, 0))
                .thenReturn(List.of(firstRecord, secondRecord));
        when(deviceMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(firstDevice, secondDevice));
        when(userMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(firstUser, secondUser));

        OverdueRecordPageResponse response = overdueService.listOverdueRecords("device-admin-1", "DEVICE_ADMIN", 1, 10, "PENDING");

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

    private OverdueRecord buildOverdueRecord(String id, String borrowRecordId, String deviceId, String userId) {
        OverdueRecord overdueRecord = new OverdueRecord();
        overdueRecord.setId(id);
        overdueRecord.setBorrowRecordId(borrowRecordId);
        overdueRecord.setDeviceId(deviceId);
        overdueRecord.setUserId(userId);
        overdueRecord.setOverdueHours(12);
        overdueRecord.setOverdueDays(1);
        overdueRecord.setProcessingStatus("PENDING");
        overdueRecord.setCreatedAt(LocalDateTime.parse("2026-03-20T12:00:00"));
        return overdueRecord;
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
