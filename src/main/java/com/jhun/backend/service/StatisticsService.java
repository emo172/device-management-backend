package com.jhun.backend.service;

import com.jhun.backend.dto.statistics.BorrowStatisticsResponse;
import com.jhun.backend.dto.statistics.CategoryUtilizationResponse;
import com.jhun.backend.dto.statistics.DeviceRankingResponse;
import com.jhun.backend.dto.statistics.DeviceUtilizationResponse;
import com.jhun.backend.dto.statistics.OverdueStatisticsResponse;
import com.jhun.backend.dto.statistics.StatisticsOverviewResponse;
import com.jhun.backend.dto.statistics.TimeSlotStatisticsResponse;
import com.jhun.backend.dto.statistics.UserRankingResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * 统计分析服务。
 * <p>
 * 负责两类核心职责：一是把事实业务数据预聚合到 {@code statistics_daily}；
 * 二是为统计接口输出总览、利用率、排行榜和热门时段等读取模型。
 */
public interface StatisticsService {

    /**
     * 查询指定日期的统计总览。
     *
     * @param statDate 统计日期
     * @return 汇总预约、借还、逾期和利用率的总览结果
     */
    StatisticsOverviewResponse getOverview(LocalDate statDate);

    /**
     * 查询设备维度利用率列表。
     *
     * @param statDate 统计日期
     * @return 按设备汇总的预约、借还、逾期与利用率结果
     */
    List<DeviceUtilizationResponse> listDeviceUtilization(LocalDate statDate);

    /**
     * 查询分类维度利用率列表。
     *
     * @param statDate 统计日期
     * @return 按分类汇总的预约、借还、逾期与利用率结果
     */
    List<CategoryUtilizationResponse> listCategoryUtilization(LocalDate statDate);

    /**
     * 查询借还统计卡片。
     *
     * @param statDate 统计日期
     * @return 当日借出数与归还数
     */
    BorrowStatisticsResponse getBorrowStatistics(LocalDate statDate);

    /**
     * 查询逾期统计卡片。
     *
     * @param statDate 统计日期
     * @return 当日逾期总数与逾期总时长
     */
    OverdueStatisticsResponse getOverdueStatistics(LocalDate statDate);

    /**
     * 查询热门预约时段。
     *
     * @param statDate 统计日期
     * @return 按预约开始小时聚合的热门时段列表
     */
    List<TimeSlotStatisticsResponse> listHotTimeSlots(LocalDate statDate);

    /**
     * 查询设备排行榜。
     *
     * @param statDate 统计日期
     * @return 按借出次数和利用率排序的设备排行
     */
    List<DeviceRankingResponse> listDeviceRanking(LocalDate statDate);

    /**
     * 查询用户借用排行榜。
     *
     * @param statDate 统计日期
     * @return 按借出次数排序的用户排行
     */
    List<UserRankingResponse> listUserRanking(LocalDate statDate);

    /**
     * 重算指定日期的统计聚合结果。
     *
     * @param statDate 需要重算的统计日期
     */
    void aggregateDailyStatistics(LocalDate statDate);
}
