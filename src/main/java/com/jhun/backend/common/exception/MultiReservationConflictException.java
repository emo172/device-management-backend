package com.jhun.backend.common.exception;

import com.jhun.backend.dto.reservation.MultiReservationConflictResponse;

/**
 * 多设备预约冲突异常。
 * <p>
 * 该异常专门用于“整单必须回滚，但前端还需要拿到阻塞设备清单”的失败场景；
 * 与通用 {@link BusinessException} 不同，它会被统一翻译成 HTTP 409，并保留 {@code blockingDevices[]} 数据。
 */
public class MultiReservationConflictException extends RuntimeException {

    /** 预约失败时返回给前端的阻塞设备清单。 */
    private final MultiReservationConflictResponse response;

    public MultiReservationConflictException(String message, MultiReservationConflictResponse response) {
        super(message);
        this.response = response;
    }

    public MultiReservationConflictResponse getResponse() {
        return response;
    }
}
