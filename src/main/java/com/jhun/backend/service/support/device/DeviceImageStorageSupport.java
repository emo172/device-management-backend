package com.jhun.backend.service.support.device;

import com.jhun.backend.common.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 设备图片存储支持组件。
 * <p>
 * 当前阶段先把上传图片保存到本地 uploads 目录，并返回可回传的相对路径，后续接入对象存储时在该组件内替换实现。
 */
@Component
public class DeviceImageStorageSupport {

    private static final String DEVICE_IMAGE_DIRECTORY = "devices";
    private static final Set<String> ALLOWED_PUBLIC_IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");

    private final Path uploadRoot;
    private final String publicBaseUrl;

    public DeviceImageStorageSupport(
            @Value("${storage.upload-dir:uploads}") String uploadDir,
            @Value("${storage.public-base-url:/files}") String publicBaseUrl) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.publicBaseUrl = normalizePublicBaseUrl(publicBaseUrl);
    }

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
            Path directory = uploadRoot.resolve(DEVICE_IMAGE_DIRECTORY);
            Files.createDirectories(directory);
            String extension = extractAllowedImageExtension(file);
            String fileName = buildStoredFileName(deviceId, extension);
            deletePreviousDeviceImages(directory, deviceId);
            Path target = directory.resolve(fileName);
            Files.write(target, file.getBytes());
            return publicBaseUrl + "/" + DEVICE_IMAGE_DIRECTORY + "/" + fileName;
        } catch (IOException exception) {
            throw new BusinessException("设备图片保存失败");
        }
    }

    /**
     * 公开静态目录只允许落图片白名单类型，
     * 避免脚本、压缩包等任意文件通过设备图片入口暴露到 `/files/devices/**`。
     */
    private String extractAllowedImageExtension(MultipartFile file) {
        String cleanedFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String leafFilename = StringUtils.getFilename(cleanedFilename);
        String extension = StringUtils.getFilenameExtension(leafFilename);
        if (!StringUtils.hasText(extension)) {
            throw new BusinessException("设备图片格式不支持");
        }
        String normalizedExtension = extension.toLowerCase(Locale.ROOT);
        if (!ALLOWED_PUBLIC_IMAGE_EXTENSIONS.contains(normalizedExtension)) {
            throw new BusinessException("设备图片格式不支持");
        }
        return normalizedExtension;
    }

    /**
     * 设备图片文件名固定锚定到设备 ID，
     * 这样重复上传时既能复用稳定公开地址，也方便在写入前清理同设备历史图片，避免磁盘持续堆积孤儿文件。
     */
    private String buildStoredFileName(String deviceId, String extension) {
        return deviceId + "." + extension;
    }

    /**
     * 同一设备重复上传图片时，旧图片必须在落盘前清理掉。
     * <p>
     * 这里同时兼容两种命名口径：
     * 一是当前稳定路径 `deviceId.ext`，二是历史随机文件名 `deviceId-uuid.ext`；
     * 这样无论设备是旧数据还是新数据，都不会因为多次换图而在 `uploads/devices/` 下遗留垃圾文件。
     */
    private void deletePreviousDeviceImages(Path directory, String deviceId) throws IOException {
        if (Files.notExists(directory)) {
            return;
        }
        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) {
                String fileName = path.getFileName().toString();
                if (fileName.startsWith(deviceId + ".") || fileName.startsWith(deviceId + "-")) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    /**
     * 对外统一输出相对资源前缀，避免配置末尾是否带 `/` 影响最终图片地址拼接结果。
     */
    private String normalizePublicBaseUrl(String configuredBaseUrl) {
        if (configuredBaseUrl == null || configuredBaseUrl.isBlank()) {
            return "/files";
        }
        return configuredBaseUrl.endsWith("/")
                ? configuredBaseUrl.substring(0, configuredBaseUrl.length() - 1)
                : configuredBaseUrl;
    }
}
