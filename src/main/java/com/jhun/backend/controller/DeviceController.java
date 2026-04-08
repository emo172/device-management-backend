package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.config.security.AuthUserPrincipal;
import com.jhun.backend.dto.device.CreateDeviceRequest;
import com.jhun.backend.dto.device.DeviceDetailResponse;
import com.jhun.backend.dto.device.DevicePageResponse;
import com.jhun.backend.dto.device.DeviceResponse;
import com.jhun.backend.dto.device.UpdateDeviceStatusRequest;
import com.jhun.backend.dto.device.UpdateDeviceRequest;
import java.time.LocalDateTime;
import com.jhun.backend.service.DeviceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 设备控制器。
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /**
     * 创建设备。
     * <p>
     * 设备生命周期管理属于 DEVICE_ADMIN 职责，SYSTEM_ADMIN 不应直接介入设备主数据维护，
     * 因此创建接口在控制层即收敛为设备管理员专用入口。
     */
    @PostMapping
    @PreAuthorize("hasRole('DEVICE_ADMIN')")
    public Result<DeviceResponse> create(@RequestBody CreateDeviceRequest request) {
        return Result.success(deviceService.createDevice(request));
    }

    /**
     * 更新设备基础信息。
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DEVICE_ADMIN')")
    public Result<DeviceResponse> update(@PathVariable("id") String deviceId, @RequestBody UpdateDeviceRequest request) {
        return Result.success(deviceService.updateDevice(deviceId, request));
    }

    /**
     * 软删除设备。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DEVICE_ADMIN')")
    public Result<DeviceResponse> delete(@PathVariable("id") String deviceId) {
        return Result.success(deviceService.softDeleteDevice(deviceId));
    }

    @GetMapping
    public Result<DevicePageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String categoryName) {
        return Result.success(deviceService.listDevices(page, size, categoryName));
    }

    /**
     * 按预约时间窗搜索可预约设备。
     * <p>
     * 该接口服务创建设备预约页，固定返回“静态状态可预约且目标时间窗无冲突”的设备，
     * 同时支持关键字命中设备名/分类名以及分类后代展开，但不会改变旧 `/api/devices` 的浏览语义。
     */
    @GetMapping("/reservable")
    public Result<DevicePageResponse> searchReservableDevices(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoryId,
            @RequestParam(defaultValue = "true") boolean includeDescendants,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(deviceService.searchReservableDevices(startTime, endTime, q, categoryId, includeDescendants, page, size));
    }

    @GetMapping("/{id}")
    public Result<DeviceDetailResponse> detail(@PathVariable("id") String deviceId) {
        return Result.success(deviceService.getDeviceDetail(deviceId));
    }

    /**
     * 上传设备图片。
     */
    @PostMapping("/{id}/image")
    @PreAuthorize("hasRole('DEVICE_ADMIN')")
    public Result<DeviceDetailResponse> uploadImage(
            @PathVariable("id") String deviceId,
            @ModelAttribute("file") MultipartFile file,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return Result.success(deviceService.uploadImage(deviceId, file, principal.userId()));
    }

    /**
     * 更新设备状态。
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('DEVICE_ADMIN')")
    public Result<DeviceResponse> updateStatus(
            @PathVariable("id") String deviceId,
            @RequestBody UpdateDeviceStatusRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal) {
        return Result.success(deviceService.updateDeviceStatus(deviceId, request, principal.userId()));
    }
}
