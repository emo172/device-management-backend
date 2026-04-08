package com.jhun.backend.service.support.reservation;

import com.jhun.backend.common.exception.BusinessException;
import com.jhun.backend.dto.reservation.BlockingDeviceResponse;
import com.jhun.backend.dto.reservation.CreateMultiReservationRequest;
import com.jhun.backend.dto.reservation.CreateReservationRequest;
import com.jhun.backend.dto.reservation.ReservationCreateSeedAccountResponse;
import com.jhun.backend.dto.reservation.ReservationCreateSeedDeviceResponse;
import com.jhun.backend.dto.reservation.ReservationCreateSeedRequest;
import com.jhun.backend.dto.reservation.ReservationCreateSeedResponse;
import com.jhun.backend.dto.reservation.ReservationResponse;
import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.DeviceCategory;
import com.jhun.backend.entity.Role;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.DeviceCategoryMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.RoleMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.ReservationService;
import com.jhun.backend.util.UuidUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * reservation-create 浏览器验证 internal seed 支撑组件。
 * <p>
 * 这里保留的类级和方法级注释属于必要说明：
 * 一方面要明确这不是公开业务能力，而是受 profile + 显式开关保护的内部造数支撑；
 * 另一方面要解释为什么账号/分类/设备用 Mapper 自举、而冲突预约继续复用正式 {@link ReservationService}，
 * 否则后续维护者很容易把它误改成公开 API 或静态 SQL 假数据出口。
 */
@Component
@Profile({"dev", "test"})
@ConditionalOnProperty(prefix = "internal.seed.reservation-create", name = "enabled", havingValue = "true")
public class ReservationCreateSeedSupport {

    private static final String HAPPY_PATH_SCENARIO = "happy-path";
    private static final String ATOMIC_FAILURE_SCENARIO = "atomic-failure";
    private static final String DEFAULT_APPROVAL_MODE = "DEVICE_ONLY";
    private static final String AVAILABLE_STATUS = "AVAILABLE";

    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final DeviceCategoryMapper deviceCategoryMapper;
    private final DeviceMapper deviceMapper;
    private final PasswordEncoder passwordEncoder;
    private final ReservationService reservationService;
    private final ReservationCreateSeedProperties reservationCreateSeedProperties;

    public ReservationCreateSeedSupport(
            RoleMapper roleMapper,
            UserMapper userMapper,
            DeviceCategoryMapper deviceCategoryMapper,
            DeviceMapper deviceMapper,
            PasswordEncoder passwordEncoder,
            ReservationService reservationService,
            ReservationCreateSeedProperties reservationCreateSeedProperties) {
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
        this.deviceCategoryMapper = deviceCategoryMapper;
        this.deviceMapper = deviceMapper;
        this.passwordEncoder = passwordEncoder;
        this.reservationService = reservationService;
        this.reservationCreateSeedProperties = reservationCreateSeedProperties;
    }

    /**
     * 生成 reservation-create 浏览器验证所需的最小真实数据。
     * <p>
     * happy-path 只准备“稍后由真实前端提交”的请求真相，不预先落目标预约；
     * atomic-failure 则会额外写入一条真实单设备预约，占住第二台设备，让后续多设备创建稳定触发整单 409。
     */
    @Transactional
    public ReservationCreateSeedResponse seed(ReservationCreateSeedRequest request) {
        String scenario = normalizeScenario(request);
        String seedKey = UuidUtil.randomUuid().substring(0, 8);

        SeededUser user = createSeededUser("rc-user", "USER", "浏览器联调普通用户", seedKey);
        SeededUser deviceAdmin = createSeededUser("rc-da", "DEVICE_ADMIN", "浏览器联调设备管理员", seedKey);
        SeededUser systemAdmin = createSeededUser("rc-sa", "SYSTEM_ADMIN", "浏览器联调系统管理员", seedKey);
        DeviceCategory category = createCategory(seedKey);
        Device firstDevice = createDevice(category, "A", seedKey);
        Device secondDevice = createDevice(category, "B", seedKey);
        LocalDateTime startTime = buildSeedStartTime();
        LocalDateTime endTime = startTime.plusHours(1);

        CreateMultiReservationRequest reservationRequest = new CreateMultiReservationRequest(
                null,
                List.of(firstDevice.getId(), secondDevice.getId()),
                startTime,
                endTime,
                "浏览器验证-" + scenario,
                "由 internal seed 入口生成的 reservation-create 场景数据");

        String conflictReservationId = null;
        List<BlockingDeviceResponse> blockingDevices = List.of();
        if (ATOMIC_FAILURE_SCENARIO.equals(scenario)) {
            ReservationResponse conflictReservation = reservationService.createReservation(
                    user.user().getId(),
                    user.user().getId(),
                    new CreateReservationRequest(
                            secondDevice.getId(),
                            startTime,
                            endTime,
                            "atomic-failure 冲突基线",
                            "预占第 2 台设备，供多设备创建验证整单 409"));
            conflictReservationId = conflictReservation.id();
            blockingDevices = List.of(new BlockingDeviceResponse(
                    secondDevice.getId(),
                    secondDevice.getName(),
                    "DEVICE_TIME_CONFLICT",
                    "设备在当前时间段已存在有效预约"));
        }

        return new ReservationCreateSeedResponse(
                scenario,
                toAccountResponse(user),
                toAccountResponse(deviceAdmin),
                toAccountResponse(systemAdmin),
                category.getId(),
                category.getName(),
                List.of(toDeviceResponse(firstDevice), toDeviceResponse(secondDevice)),
                reservationRequest,
                blockingDevices,
                conflictReservationId);
    }

    private String normalizeScenario(ReservationCreateSeedRequest request) {
        if (request == null || request.scenario() == null || request.scenario().isBlank()) {
            throw new BusinessException("场景名不能为空");
        }
        String scenario = request.scenario().trim();
        if (HAPPY_PATH_SCENARIO.equals(scenario) || ATOMIC_FAILURE_SCENARIO.equals(scenario)) {
            return scenario;
        }
        throw new BusinessException("仅支持 happy-path 与 atomic-failure 场景");
    }

    private SeededUser createSeededUser(String prefix, String roleName, String realName, String seedKey) {
        Role role = roleMapper.findByName(roleName);
        if (role == null) {
            throw new BusinessException("缺少角色种子数据：" + roleName);
        }
        String accountSuffix = seedKey.toLowerCase();
        String username = prefix + "-" + accountSuffix;
        String actualPassword = buildSeedPassword(roleName, seedKey);
        User user = new User();
        user.setId(UuidUtil.randomUuid());
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(actualPassword));
        user.setRoleId(role.getId());
        user.setRealName(realName);
        user.setPhone(buildPhone(seedKey + roleName));
        user.setStatus(1);
        user.setFreezeStatus("NORMAL");
        userMapper.insert(user);
        String exposedPassword = shouldExposePassword(roleName) ? actualPassword : null;
        return new SeededUser(user, roleName, exposedPassword);
    }

    private DeviceCategory createCategory(String seedKey) {
        DeviceCategory category = new DeviceCategory();
        category.setId(UuidUtil.randomUuid());
        category.setName("预约创建联调分类-" + seedKey);
        category.setSortOrder(1);
        category.setDescription("仅供 reservation-create 浏览器验证使用的最小 internal seed 分类");
        category.setDefaultApprovalMode(DEFAULT_APPROVAL_MODE);
        deviceCategoryMapper.insert(category);
        return category;
    }

    private Device createDevice(DeviceCategory category, String slot, String seedKey) {
        Device device = new Device();
        device.setId(UuidUtil.randomUuid());
        device.setName("预约创建联调设备-" + slot + "-" + seedKey);
        device.setDeviceNumber("RC-" + slot + "-" + seedKey.toUpperCase());
        device.setCategoryId(category.getId());
        device.setStatus(AVAILABLE_STATUS);
        device.setDescription("reservation-create 浏览器验证 internal seed 设备");
        device.setLocation("Reservation-Create-Lab");
        deviceMapper.insert(device);
        return device;
    }

    private LocalDateTime buildSeedStartTime() {
        return LocalDate.now().plusDays(2).atTime(10, 0);
    }

    private ReservationCreateSeedAccountResponse toAccountResponse(SeededUser seededUser) {
        return new ReservationCreateSeedAccountResponse(
                seededUser.user().getId(),
                seededUser.user().getUsername(),
                seededUser.user().getEmail(),
                seededUser.user().getUsername(),
                seededUser.exposedPassword(),
                seededUser.roleName());
    }

    /**
     * 生成一次性联调密码。
     * <p>
     * 普通用户密码会直接回传给脚本用于登录联调，因此既要可读又要满足密码复杂度；
     * 管理员账号即便默认不回传密码，也不应继续共用可猜测的固定弱口令。
     */
    private String buildSeedPassword(String roleName, String seedKey) {
        String roleFragment = roleName.substring(0, Math.min(roleName.length(), 3)).toLowerCase();
        String randomFragment = UuidUtil.randomUuid().replace("-", "").substring(0, 6);
        return "Seed-" + roleFragment + "-" + seedKey.toLowerCase() + "-" + randomFragment + "-Aa1!";
    }

    /**
     * 默认只暴露普通用户的明文密码。
     * <p>
     * reservation-create 浏览器联调主要依赖普通用户登录场景；
     * 设备管理员和系统管理员账号只需要作为真实数据存在，不应默认通过 seed 响应直接分发其明文口令。
     */
    private boolean shouldExposePassword(String roleName) {
        return "USER".equals(roleName) || reservationCreateSeedProperties.isExposeAdminPasswords();
    }

    private ReservationCreateSeedDeviceResponse toDeviceResponse(Device device) {
        return new ReservationCreateSeedDeviceResponse(device.getId(), device.getName(), device.getDeviceNumber());
    }

    private String buildPhone(String seed) {
        int value = Math.floorMod(seed.hashCode(), 100_000_000);
        return "138" + String.format("%08d", value);
    }

    private record SeededUser(User user, String roleName, String exposedPassword) {
    }
}
