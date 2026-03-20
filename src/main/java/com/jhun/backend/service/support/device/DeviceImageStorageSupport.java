package com.jhun.backend.service.support.device;

import com.jhun.backend.common.exception.BusinessException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    private final ConcurrentMap<String, DeviceUploadLock> deviceUploadLocks = new ConcurrentHashMap<>();

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
        DeviceUploadLock uploadLock = acquireUploadLock(deviceId);
        uploadLock.lock();
        Path temporaryTarget = null;
        boolean releaseInFinally = true;
        try {
            if (file == null || file.isEmpty()) {
                throw new BusinessException("上传文件不能为空");
            }
            Path directory = uploadRoot.resolve(DEVICE_IMAGE_DIRECTORY);
            Files.createDirectories(directory);
            String extension = extractAllowedImageExtension(file);
            String fileName = buildStoredFileName(deviceId, extension);
            Path target = directory.resolve(fileName);
            temporaryTarget = Files.createTempFile(directory, deviceId + "-", ".upload");
            Files.write(temporaryTarget, file.getBytes());
            replaceStoredImage(temporaryTarget, target);
            releaseInFinally = !registerCleanupAfterCommit(deviceId, directory, fileName, uploadLock);

            if (releaseInFinally) {
                cleanupPreviousDeviceImagesSafely(directory, deviceId, fileName);
            }

            return publicBaseUrl + "/" + DEVICE_IMAGE_DIRECTORY + "/" + fileName;
        } catch (IOException exception) {
            throw new BusinessException("设备图片保存失败");
        } finally {
            if (temporaryTarget != null) {
                try {
                    Files.deleteIfExists(temporaryTarget);
                } catch (IOException ignored) {
                    // 临时文件清理失败不改变主异常口径，下一次上传仍会按设备 ID 前缀继续收敛清理。
                }
            }
            if (releaseInFinally) {
                releaseUploadLock(deviceId, uploadLock);
            }
        }
    }

    /**
     * 设备图片替换属于“文件系统先成功、数据库后提交”的跨资源链路。
     * <p>
     * 若当前存在事务，就必须把旧图清理与锁释放延后到事务回调里：
     * - `afterCommit` 才清理旧图，避免数据库回滚后旧地址断链；
     * - `afterCompletion` 才释放设备锁，避免同一设备的下一次上传在前一笔事务提交前闯入并删错文件。
     */
    private boolean registerCleanupAfterCommit(String deviceId, Path directory, String retainedFileName, DeviceUploadLock uploadLock) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cleanupPreviousDeviceImagesSafely(directory, deviceId, retainedFileName);
            }

            @Override
            public void afterCompletion(int status) {
                releaseUploadLock(deviceId, uploadLock);
            }
        });
        return true;
    }

    private DeviceUploadLock acquireUploadLock(String deviceId) {
        return deviceUploadLocks.compute(deviceId, (ignoredDeviceId, existingLock) -> {
            DeviceUploadLock nextLock = existingLock == null ? new DeviceUploadLock() : existingLock;
            nextLock.retain();
            return nextLock;
        });
    }

    /**
     * 同一设备的图片上传必须串行执行。
     * <p>
     * 这里在释放锁的同时按引用计数回收空闲锁实例，避免不同设备无限增长占用内存；
     * 释放时显式带上 `deviceId`，不能再靠遍历 map 反查当前锁，否则并发窗口里容易找错 key 或触发额外竞态。
     */
    private void releaseUploadLock(String deviceId, DeviceUploadLock uploadLock) {
        try {
            uploadLock.unlock();
        } finally {
            deviceUploadLocks.computeIfPresent(deviceId, (ignoredDeviceId, existingLock) -> {
                if (existingLock != uploadLock) {
                    return existingLock;
                }
                return uploadLock.releaseReference() == 0 ? null : uploadLock;
            });
        }
    }

    /**
     * 先把新图完整写入临时文件，再原子替换正式路径。
     * 这样即便替换阶段失败，旧图仍然保留在正式地址上，不会出现数据库回滚但公开图片已被提前删掉的断链状态。
     */
    void replaceStoredImage(Path temporaryTarget, Path finalTarget) throws IOException {
        try {
            Files.move(
                    temporaryTarget,
                    finalTarget,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryTarget, finalTarget, StandardCopyOption.REPLACE_EXISTING);
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
     * 每次上传都生成新的版本化文件名，而不是直接覆盖旧文件。
     * <p>
     * 这样即便数据库事务随后回滚，旧 `image_url` 仍然指向原文件，不会出现“接口失败但公开图片已被新内容覆盖”的跨资源不一致；
     * 提交成功后再配合同设备历史文件清理，最终仍能把磁盘收敛到单份有效图片。
     */
    private String buildStoredFileName(String deviceId, String extension) {
        return deviceId + "-" + UUID.randomUUID() + "." + extension;
    }

    /**
     * 同一设备重复上传图片时，旧图片只允许在新版本成为正式图片后再清理。
     * <p>
     * 这里兼容历史稳定路径 `deviceId.ext` 与当前版本化路径 `deviceId-uuid.ext`，
     * 让历史存量数据在下一次成功换图后也能一并完成垃圾文件收敛。
     */
    private void deletePreviousDeviceImages(Path directory, String deviceId, String retainedFileName) throws IOException {
        if (Files.notExists(directory)) {
            return;
        }
        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) {
                String fileName = path.getFileName().toString();
                if (fileName.equals(retainedFileName)) {
                    continue;
                }
                if (fileName.startsWith(deviceId + ".") || fileName.startsWith(deviceId + "-")) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    /**
     * 历史垃圾文件清理只做 best-effort。
     * <p>
     * 新图已经成为当前正式路径后，清理失败最多意味着磁盘多留一份旧图，
     * 不能把上传结果反向改判成失败，也不能影响事务提交后的公开图片可用性。
     */
    private void cleanupPreviousDeviceImagesSafely(Path directory, String deviceId, String retainedFileName) {
        try {
            deletePreviousDeviceImages(directory, deviceId, retainedFileName);
        } catch (IOException ignored) {
            // 旧图清理失败只影响磁盘卫生，不影响当前公开图片的正确性。
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

    private static final class DeviceUploadLock {

        private final ReentrantLock delegate = new ReentrantLock();
        private final AtomicInteger references = new AtomicInteger();

        private void retain() {
            references.incrementAndGet();
        }

        private void lock() {
            delegate.lock();
        }

        private void unlock() {
            delegate.unlock();
        }

        private int releaseReference() {
            return references.decrementAndGet();
        }
    }
}
