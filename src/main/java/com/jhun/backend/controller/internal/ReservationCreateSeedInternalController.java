package com.jhun.backend.controller.internal;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.dto.reservation.ReservationCreateSeedRequest;
import com.jhun.backend.dto.reservation.ReservationCreateSeedResponse;
import com.jhun.backend.service.support.reservation.ReservationCreateSeedSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * reservation-create 浏览器验证 internal seed 控制器。
 * <p>
 * 这是一个刻意收敛的 test/dev 内部入口：
 * 只服务后续 `scripts/e2e/seed-reservation-create.mjs` 调真实后端准备场景真相，
 * 默认生产不会注册，且只接受固定场景名，避免演化成对外可用的通用造数 API。
 */
@RestController
@Profile({"dev", "test"})
@ConditionalOnProperty(prefix = "internal.seed.reservation-create", name = "enabled", havingValue = "true")
@RequestMapping("/api/internal/seeds/reservation-create")
public class ReservationCreateSeedInternalController {

    private final ReservationCreateSeedSupport reservationCreateSeedSupport;

    public ReservationCreateSeedInternalController(ReservationCreateSeedSupport reservationCreateSeedSupport) {
        this.reservationCreateSeedSupport = reservationCreateSeedSupport;
    }

    /**
     * 生成 reservation-create 浏览器验证所需的最小真实数据。
     * <p>
     * 这里不要求登录态，因为入口自身已经被 profile + 显式开关双重保护；
     * 若再要求管理员鉴权，就会回到“无法从 0 自举 SYSTEM_ADMIN / DEVICE_ADMIN”的死循环。
     */
    @PostMapping
    public Result<ReservationCreateSeedResponse> seed(@RequestBody ReservationCreateSeedRequest request) {
        return Result.success(reservationCreateSeedSupport.seed(request));
    }
}
