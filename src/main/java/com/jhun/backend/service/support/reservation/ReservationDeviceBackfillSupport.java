package com.jhun.backend.service.support.reservation;

import com.jhun.backend.entity.ReservationDevice;
import com.jhun.backend.mapper.ReservationDeviceMapper;
import com.jhun.backend.util.UuidUtil;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预约设备关联回填支持组件。
 * <p>
 * 用于承接 expand → backfill → cutover 中的 backfill / cutover 动作：
 * 一方面把历史旧数据补成关联记录，另一方面为新预约写入正式的主设备关联。
 */
@Component
public class ReservationDeviceBackfillSupport {

    private final ReservationDeviceMapper reservationDeviceMapper;

    public ReservationDeviceBackfillSupport(ReservationDeviceMapper reservationDeviceMapper) {
        this.reservationDeviceMapper = reservationDeviceMapper;
    }

    /**
     * 回填所有仍缺失关联记录的历史单设备预约。
     * <p>
     * 该方法必须保持幂等：重复执行时，只会补齐尚未迁移的旧预约，不会为已完成 cutover 的记录制造重复关联。
     */
    @Transactional
    public int backfillLegacyReservations() {
        List<ReservationDevice> missingRelations = reservationDeviceMapper.findLegacyReservationsWithoutRelation();
        for (ReservationDevice missingRelation : missingRelations) {
            saveDeviceRelation(missingRelation.getReservationId(), missingRelation.getDeviceId(), 0);
        }
        return missingRelations.size();
    }

    /**
     * 为新预约写入主设备关联。
     */
    public void savePrimaryDeviceRelation(String reservationId, String deviceId) {
        saveDeviceRelations(reservationId, List.of(deviceId));
    }

    /**
     * 为单条预约按顺序写入全部设备关联。
     * <p>
     * 多设备预约下，{@code device_order} 就是后续主设备兼容投影与完整设备清单排序的唯一事实来源，
     * 因此这里必须严格保留请求顺序，而不是随意按数据库返回顺序落库。
     */
    public void saveDeviceRelations(String reservationId, List<String> deviceIds) {
        List<String> orderedDeviceIds = new ArrayList<>(deviceIds);
        for (int index = 0; index < orderedDeviceIds.size(); index++) {
            saveDeviceRelation(reservationId, orderedDeviceIds.get(index), index);
        }
    }

    private void saveDeviceRelation(String reservationId, String deviceId, int deviceOrder) {
        ReservationDevice reservationDevice = new ReservationDevice();
        reservationDevice.setId(UuidUtil.randomUuid());
        reservationDevice.setReservationId(reservationId);
        reservationDevice.setDeviceId(deviceId);
        reservationDevice.setDeviceOrder(deviceOrder);
        reservationDeviceMapper.insert(reservationDevice);
    }
}
