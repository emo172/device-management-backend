package com.jhun.backend.service.support.device;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

import com.jhun.backend.common.exception.BusinessException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DeviceImageStorageSupportTest {

    @TempDir
    Path tempDir;

    /**
     * 新图写入失败时，旧图必须继续保留在正式路径上。
     * 该测试用于防止“先删旧图再写新图”导致数据库回滚后公开图片断链的回归。
     */
    @Test
    void shouldKeepPreviousImageWhenReplacingFileFails() throws Exception {
        DeviceImageStorageSupport storageSupport = spy(new DeviceImageStorageSupport(tempDir.toString(), "/files"));
        MockMultipartFile firstImage = new MockMultipartFile(
                "file",
                "first.png",
                "image/png",
                "first-image".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile secondImage = new MockMultipartFile(
                "file",
                "second.png",
                "image/png",
                "second-image".getBytes(StandardCharsets.UTF_8));

        String firstImageUrl = storageSupport.store("device-1", firstImage);

        Path storedImage = resolveStoredImagePath(firstImageUrl);

        doThrow(new IOException("disk full"))
                .when(storageSupport)
                .replaceStoredImage(any(Path.class), any(Path.class));

        assertThrows(BusinessException.class, () -> storageSupport.store("device-1", secondImage));

        assertArrayEquals("first-image".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(storedImage));
        assertEquals(1L, Files.list(tempDir.resolve("devices")).count());
    }

    /**
     * 历史垃圾文件清理失败只能降级为 best-effort，不能反向把新图上传链路打成失败。
     * 否则数据库会回滚，而文件系统里的新图已经落盘，最终仍会造成 `image_url` 与磁盘状态失配。
     */
    @Test
    void shouldKeepNewImageUrlWhenDeletingStaleImageFails() throws Exception {
        DeviceImageStorageSupport storageSupport = new DeviceImageStorageSupport(tempDir.toString(), "/files");
        MockMultipartFile firstImage = new MockMultipartFile(
                "file",
                "first.png",
                "image/png",
                "first-image".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile secondImage = new MockMultipartFile(
                "file",
                "second.jpg",
                "image/jpeg",
                "second-image".getBytes(StandardCharsets.UTF_8));

        String firstImageUrl = storageSupport.store("device-2", firstImage);

        Path staleImage = resolveStoredImagePath(firstImageUrl);

        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.deleteIfExists(eq(staleImage)))
                    .thenThrow(new IOException("cleanup failed"));

            String imageUrl = storageSupport.store("device-2", secondImage);
            org.junit.jupiter.api.Assertions.assertTrue(imageUrl.startsWith("/files/devices/device-2-"));
            assertEquals(2L, Files.list(tempDir.resolve("devices")).count());
            assertArrayEquals("second-image".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(resolveStoredImagePath(imageUrl)));
        }
    }

    /**
     * 扩展名变化时，旧图清理必须延后到事务提交之后。
     * 提交前两份文件可以短暂并存；只有确认数据库已经持久化新 `imageUrl` 后，旧图才允许被清走。
     */
    @Test
    void shouldDeleteStaleImageAfterTransactionCommit() throws Exception {
        DeviceImageStorageSupport storageSupport = new DeviceImageStorageSupport(tempDir.toString(), "/files");
        MockMultipartFile firstImage = new MockMultipartFile(
                "file",
                "first.png",
                "image/png",
                "first-image".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile secondImage = new MockMultipartFile(
                "file",
                "second.jpg",
                "image/jpeg",
                "second-image".getBytes(StandardCharsets.UTF_8));

        String firstImageUrl = storageSupport.store("device-4", firstImage);

        Path staleImage = resolveStoredImagePath(firstImageUrl);
        Path currentImage;

        TransactionSynchronizationManager.initSynchronization();
        try {
            String imageUrl = storageSupport.store("device-4", secondImage);
            currentImage = resolveStoredImagePath(imageUrl);

            org.junit.jupiter.api.Assertions.assertTrue(imageUrl.startsWith("/files/devices/device-4-"));
            org.junit.jupiter.api.Assertions.assertTrue(Files.exists(staleImage));
            org.junit.jupiter.api.Assertions.assertTrue(Files.exists(currentImage));

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
                synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(staleImage));
        assertArrayEquals("second-image".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(currentImage));
    }

    /**
     * 若事务在新图落盘后回滚，旧图必须继续保留。
     * 这样数据库回到旧 `imageUrl` 时，公开静态地址仍然可读，不会因为提前清理旧扩展名而断链。
     */
    @Test
    void shouldKeepPreviousImageWhenTransactionRollsBackAfterExtensionChange() throws Exception {
        DeviceImageStorageSupport storageSupport = new DeviceImageStorageSupport(tempDir.toString(), "/files");
        MockMultipartFile firstImage = new MockMultipartFile(
                "file",
                "first.png",
                "image/png",
                "first-image".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile secondImage = new MockMultipartFile(
                "file",
                "second.jpg",
                "image/jpeg",
                "second-image".getBytes(StandardCharsets.UTF_8));

        String firstImageUrl = storageSupport.store("device-5", firstImage);

        Path staleImage = resolveStoredImagePath(firstImageUrl);
        Path currentImage;

        TransactionSynchronizationManager.initSynchronization();
        try {
            currentImage = resolveStoredImagePath(storageSupport.store("device-5", secondImage));

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertArrayEquals("first-image".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(staleImage));
        assertArrayEquals("second-image".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(currentImage));
    }

    /**
     * 同一设备的并发上传必须串行化执行。
     * 该测试通过卡住首个请求的替换阶段，验证第二个请求不会提前进入正式替换窗口，防止两个上传互删彼此刚写出的正式文件。
     */
    @Test
    void shouldSerializeUploadsForSameDeviceId() throws Exception {
        DeviceImageStorageSupport storageSupport = spy(new DeviceImageStorageSupport(tempDir.toString(), "/files"));
        MockMultipartFile firstImage = new MockMultipartFile(
                "file",
                "first.png",
                "image/png",
                "first-image".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile secondImage = new MockMultipartFile(
                "file",
                "second.jpg",
                "image/jpeg",
                "second-image".getBytes(StandardCharsets.UTF_8));
        CountDownLatch firstReplaceEntered = new CountDownLatch(1);
        CountDownLatch allowFirstReplaceToContinue = new CountDownLatch(1);
        CountDownLatch secondReplaceEntered = new CountDownLatch(1);
        AtomicInteger replaceCallCount = new AtomicInteger();

        doAnswer(invocation -> {
            int currentCall = replaceCallCount.incrementAndGet();
            if (currentCall == 1) {
                firstReplaceEntered.countDown();
                if (!allowFirstReplaceToContinue.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("首个上传替换阶段未按预期继续执行");
                }
            } else if (currentCall == 2) {
                secondReplaceEntered.countDown();
            }
            return invocation.callRealMethod();
        }).when(storageSupport).replaceStoredImage(any(Path.class), any(Path.class));

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> firstFuture = executor.submit(() -> storageSupport.store("device-3", firstImage));
            if (!firstReplaceEntered.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("首个上传未进入替换阶段");
            }

            Future<String> secondFuture = executor.submit(() -> storageSupport.store("device-3", secondImage));

            org.junit.jupiter.api.Assertions.assertFalse(secondReplaceEntered.await(200, TimeUnit.MILLISECONDS));

            allowFirstReplaceToContinue.countDown();

            String firstImageUrl = firstFuture.get(2, TimeUnit.SECONDS);
            String secondImageUrl = secondFuture.get(2, TimeUnit.SECONDS);

            org.junit.jupiter.api.Assertions.assertTrue(firstImageUrl.startsWith("/files/devices/device-3-"));
            org.junit.jupiter.api.Assertions.assertTrue(secondImageUrl.startsWith("/files/devices/device-3-"));
            org.junit.jupiter.api.Assertions.assertNotEquals(firstImageUrl, secondImageUrl);
        }
    }

    private Path resolveStoredImagePath(String imageUrl) {
        return tempDir.resolve("devices").resolve(imageUrl.substring(imageUrl.lastIndexOf('/') + 1));
    }
}
