package com.jhun.backend.integration.notification;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jhun.backend.config.security.JwtTokenProvider;
import com.jhun.backend.entity.NotificationRecord;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.NotificationRecordMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 通知控制器集成测试。
 * <p>
 * 该测试锁定通知列表、未读数、单条已读和全部已读接口的最小闭环，确保前端通知页可以尽早联调。
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private NotificationRecordMapper notificationRecordMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * 验证用户可以查询未读通知数量，保护通知角标数据来源。
     */
    @Test
    void shouldReturnUnreadCount() throws Exception {
        User user = createUser("notice-user", "notice@example.com");
        insertNotification(user.getId(), "IN_APP", 0);
        insertNotification(user.getId(), "IN_APP", 0);
        insertNotification(user.getId(), "EMAIL", 0);

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2));
    }

    /**
     * 验证用户可以将站内信标记为已读，且仅影响 IN_APP 渠道记录。
     */
    @Test
    void shouldMarkNotificationAsRead() throws Exception {
        User user = createUser("notice-user-2", "notice2@example.com");
        NotificationRecord notification = insertNotification(user.getId(), "IN_APP", 0);

        mockMvc.perform(put("/api/notifications/{id}/read", notification.getId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readFlag").value(1));
    }

    /**
     * 验证用户可以批量已读全部站内信，保护通知页“全部已读”动作。
     */
    @Test
    void shouldMarkAllInAppNotificationsAsRead() throws Exception {
        User user = createUser("notice-user-3", "notice3@example.com");
        insertNotification(user.getId(), "IN_APP", 0);
        insertNotification(user.getId(), "IN_APP", 0);

        mockMvc.perform(put("/api/notifications/read-all")
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(2));
    }

    private User createUser(String username, String email) {
        Role role = roleMapper.findByName("USER");
        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRoleId(role.getId());
        user.setRealName(username);
        user.setPhone("13800138111");
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        return user;
    }

    private NotificationRecord insertNotification(String userId, String channel, Integer readFlag) {
        NotificationRecord record = new NotificationRecord();
        record.setId(UuidUtil.randomUuid());
        record.setUserId(userId);
        record.setNotificationType("VERIFY_CODE");
        record.setChannel(channel);
        record.setTitle("测试通知");
        record.setContent("测试内容");
        record.setStatus("SUCCESS");
        record.setRetryCount(0);
        record.setReadFlag(readFlag);
        notificationRecordMapper.insert(record);
        return record;
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), "USER");
    }
}
