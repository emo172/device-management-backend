package com.jhun.backend.integration.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhun.backend.entity.Reservation;
import com.jhun.backend.mapper.DeviceCategoryMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.ReservationDeviceMapper;
import com.jhun.backend.mapper.ReservationMapper;
import com.jhun.backend.mapper.UserMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * reservation-create internal seed 入口集成测试。
 * <p>
 * 该测试锁定两个最重要的回归点：
 * happy-path 只能准备真实可登录账号和设备真相，不能提前污染目标预约；
 * atomic-failure 必须额外落一条真实冲突预约，供后续多设备创建稳定触发整单 409。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "internal.seed.reservation-create.enabled=true")
class ReservationCreateSeedInternalControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DeviceCategoryMapper deviceCategoryMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private ReservationMapper reservationMapper;

    @Autowired
    private ReservationDeviceMapper reservationDeviceMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 验证 happy-path 只准备后续创建所需真相，不会提前写入目标预约本身。
     */
    @Test
    void seedsHappyPathWithoutCreatingTargetReservation() throws Exception {
        long beforeUserCount = userMapper.selectCount(null);
        long beforeCategoryCount = deviceCategoryMapper.selectCount(null);
        long beforeDeviceCount = deviceMapper.selectCount(null);
        long beforeReservationCount = reservationMapper.selectCount(null);

        MvcResult result = mockMvc.perform(post("/api/internal/seeds/reservation-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scenario": "happy-path"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scenario").value("happy-path"))
                .andExpect(jsonPath("$.data.userAccount.role").value("USER"))
                .andExpect(jsonPath("$.data.deviceAdminAccount.role").value("DEVICE_ADMIN"))
                .andExpect(jsonPath("$.data.systemAdminAccount.role").value("SYSTEM_ADMIN"))
                .andExpect(jsonPath("$.data.devices.length()").value(2))
                .andExpect(jsonPath("$.data.blockingDevices.length()").value(0))
                .andReturn();

        JsonNode data = readData(result);
        String categoryId = data.path("categoryId").asText();
        String firstDeviceId = data.path("devices").get(0).path("deviceId").asText();
        String secondDeviceId = data.path("devices").get(1).path("deviceId").asText();
        String userId = data.path("userAccount").path("userId").asText();
        String deviceAdminId = data.path("deviceAdminAccount").path("userId").asText();
        String systemAdminId = data.path("systemAdminAccount").path("userId").asText();

        assertEquals(beforeUserCount + 3, userMapper.selectCount(null));
        assertEquals(beforeCategoryCount + 1, deviceCategoryMapper.selectCount(null));
        assertEquals(beforeDeviceCount + 2, deviceMapper.selectCount(null));
        assertEquals(beforeReservationCount, reservationMapper.selectCount(null));
        assertNotNull(userMapper.selectById(userId));
        assertNotNull(userMapper.selectById(deviceAdminId));
        assertNotNull(userMapper.selectById(systemAdminId));
        assertNotNull(deviceCategoryMapper.selectById(categoryId));
        assertNotNull(deviceMapper.selectById(firstDeviceId));
        assertNotNull(deviceMapper.selectById(secondDeviceId));
        assertTrue(data.path("conflictReservationId").isNull());

        JsonNode reservationRequest = data.path("reservationRequest");
        assertEquals(firstDeviceId, reservationRequest.path("deviceIds").get(0).asText());
        assertEquals(secondDeviceId, reservationRequest.path("deviceIds").get(1).asText());
        assertEquals(LocalDateTime.parse(reservationRequest.path("startTime").asText()).plusHours(1),
                LocalDateTime.parse(reservationRequest.path("endTime").asText()));
    }

    /**
     * 验证 atomic-failure 会真实写入一条冲突预约，并把阻塞设备真相返回给前端 seed 脚本。
     */
    @Test
    void seedsAtomicFailureWithRealConflictReservation() throws Exception {
        long beforeReservationCount = reservationMapper.selectCount(null);

        MvcResult result = mockMvc.perform(post("/api/internal/seeds/reservation-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scenario": "atomic-failure"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scenario").value("atomic-failure"))
                .andExpect(jsonPath("$.data.devices.length()").value(2))
                .andExpect(jsonPath("$.data.blockingDevices.length()").value(1))
                .andExpect(jsonPath("$.data.blockingDevices[0].reasonCode").value("DEVICE_TIME_CONFLICT"))
                .andReturn();

        JsonNode data = readData(result);
        String conflictReservationId = data.path("conflictReservationId").asText();
        String blockedDeviceId = data.path("blockingDevices").get(0).path("deviceId").asText();
        LocalDateTime startTime = LocalDateTime.parse(data.path("reservationRequest").path("startTime").asText());
        LocalDateTime endTime = LocalDateTime.parse(data.path("reservationRequest").path("endTime").asText());

        assertEquals(beforeReservationCount + 1, reservationMapper.selectCount(null));
        Reservation conflictReservation = reservationMapper.findAggregateById(conflictReservationId);
        assertNotNull(conflictReservation);
        assertEquals(blockedDeviceId, conflictReservation.getDeviceId());
        assertEquals(startTime, conflictReservation.getStartTime());
        assertEquals(endTime, conflictReservation.getEndTime());
        assertEquals(1L, reservationDeviceMapper.countByReservationId(conflictReservationId));
        assertEquals(blockedDeviceId, reservationDeviceMapper.findByReservationId(conflictReservationId).getFirst().getDeviceId());
        assertEquals(1, reservationMapper.findConflictingReservations(blockedDeviceId, startTime, endTime).size());
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
