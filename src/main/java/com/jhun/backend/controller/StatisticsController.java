package com.jhun.backend.controller;

import com.jhun.backend.common.response.Result;
import com.jhun.backend.dto.statistics.BorrowStatisticsResponse;
import com.jhun.backend.dto.statistics.CategoryUtilizationResponse;
import com.jhun.backend.dto.statistics.DeviceRankingResponse;
import com.jhun.backend.dto.statistics.DeviceUtilizationResponse;
import com.jhun.backend.dto.statistics.OverdueStatisticsResponse;
import com.jhun.backend.dto.statistics.StatisticsOverviewResponse;
import com.jhun.backend.dto.statistics.TimeSlotStatisticsResponse;
import com.jhun.backend.dto.statistics.UserRankingResponse;
import com.jhun.backend.service.StatisticsService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计分析控制器。
 * <p>
 * 该控制器统一承载管理端统计页所需的总览、利用率、借用统计、逾期统计、热门时段和排行榜接口，
 * 并通过 SYSTEM_ADMIN 角色限制确保普通用户和设备管理员不能读取全局经营数据。
 */
@RestController
@RequestMapping("/api/statistics")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * 查询统计总览。
     *
     * @param date 统计日期，未传时默认取当天
     * @return 统计总览
     */
    @GetMapping("/overview")
    public Result<StatisticsOverviewResponse> overview(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(statisticsService.getOverview(resolveDate(date)));
    }

    /**
     * 查询设备利用率。
     */
    @GetMapping("/device-utilization")
    public Result<List<DeviceUtilizationResponse>> deviceUtilization(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(statisticsService.listDeviceUtilization(resolveDate(date)));
    }

    /**
     * 查询分类利用率。
     */
    @GetMapping("/category-utilization")
    public Result<List<CategoryUtilizationResponse>> categoryUtilization(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(statisticsService.listCategoryUtilization(resolveDate(date)));
    }

    /**
     * 查询借用统计。
     */
    @GetMapping("/borrow")
    public Result<BorrowStatisticsResponse> borrow(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(statisticsService.getBorrowStatistics(resolveDate(date)));
    }

    /**
     * 查询逾期统计。
     */
    @GetMapping("/overdue")
    public Result<OverdueStatisticsResponse> overdue(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(statisticsService.getOverdueStatistics(resolveDate(date)));
    }

    /**
     * 查询热门时段。
     */
    @GetMapping("/hot-time-slots")
    public Result<List<TimeSlotStatisticsResponse>> hotTimeSlots(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(statisticsService.listHotTimeSlots(resolveDate(date)));
    }

    /**
     * 查询设备排行榜。
     */
    @GetMapping("/device-ranking")
    public Result<List<DeviceRankingResponse>> deviceRanking(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(statisticsService.listDeviceRanking(resolveDate(date)));
    }

    /**
     * 查询用户排行榜。
     */
    @GetMapping("/user-ranking")
    public Result<List<UserRankingResponse>> userRanking(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(statisticsService.listUserRanking(resolveDate(date)));
    }

    private LocalDate resolveDate(LocalDate date) {
        return date == null ? LocalDate.now() : date;
    }
}
