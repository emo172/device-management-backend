package com.jhun.backend.service.support.reservation;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 预约设备关联回填启动器。
 * <p>
 * 系统完成启动后立即执行一次历史回填，确保升级后旧单设备预约会尽快拥有正式关联记录，
 * 避免后续读路径或扩展功能继续碰到“只有兼容列、没有聚合关联”的过渡态脏数据。
 */
@Component
@Order(1)
public class ReservationDeviceBackfillInitializer implements ApplicationRunner {

    private final ReservationDeviceBackfillSupport reservationDeviceBackfillSupport;

    public ReservationDeviceBackfillInitializer(ReservationDeviceBackfillSupport reservationDeviceBackfillSupport) {
        this.reservationDeviceBackfillSupport = reservationDeviceBackfillSupport;
    }

    @Override
    public void run(ApplicationArguments args) {
        reservationDeviceBackfillSupport.backfillLegacyReservations();
    }
}
