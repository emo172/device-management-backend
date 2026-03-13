package com.jhun.backend.service.support.device;

import com.jhun.backend.common.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 设备图片存储支持组件。
 * <p>
 * 当前阶段先把上传图片保存到本地 uploads 目录，并返回可回传的相对路径，后续接入对象存储时在该组件内替换实现。
 */
@Component
public class DeviceImageStorageSupport {

    /**
     * 保存设备图片并返回相对访问路径。
     *
     * @param deviceId 设备 ID
     * @param file 上传文件
     * @return 相对图片路径
     */
    public String store(String deviceId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        try {
            Path directory = Paths.get("uploads", "devices");
            Files.createDirectories(directory);
            String fileName = deviceId + "-" + file.getOriginalFilename();
            Path target = directory.resolve(fileName);
            Files.write(target, file.getBytes());
            return "/uploads/devices/" + fileName;
        } catch (IOException exception) {
            throw new BusinessException("设备图片保存失败");
        }
    }
}
