package com.jhun.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jhun.backend.entity.StatisticsDaily;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 统计日聚合数据访问接口。
 * <p>
 * 该接口同时承担两类职责：一是从预约、借还和逾期事实表生成按日聚合结果；
 * 二是按统计类型读取 {@code statistics_daily} 中已经预聚合好的结果供接口层使用。
 */
@Mapper
public interface StatisticsDailyMapper extends BaseMapper<StatisticsDaily> {

    /**
     * 删除某个统计日已有的全部聚合结果。
     *
     * @param statDate 统计日期
     * @return 删除行数
     */
    int deleteByStatDate(@Param("statDate") LocalDate statDate);

    /**
     * 汇总全局设备利用率总览。
     *
     * @param statDate 统计日期
     * @param rangeStart 当日开始时间
     * @param rangeEnd 次日开始时间，作为右开区间上界
     * @return 全局设备利用率汇总行
     */
    StatisticsDaily summarizeGlobalDeviceUtilization(
            @Param("statDate") LocalDate statDate,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);

    /**
     * 汇总设备维度利用率列表。
     *
     * @param statDate 统计日期
     * @param rangeStart 当日开始时间
     * @param rangeEnd 次日开始时间，作为右开区间上界
     * @return 设备维度聚合结果
     */
    List<StatisticsDaily> summarizeDeviceUtilization(
            @Param("statDate") LocalDate statDate,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);

    /**
     * 汇总全局分类利用率总览。
     *
     * @param statDate 统计日期
     * @param rangeStart 当日开始时间
     * @param rangeEnd 次日开始时间，作为右开区间上界
     * @return 全局分类利用率汇总行
     */
    StatisticsDaily summarizeGlobalCategoryUtilization(
            @Param("statDate") LocalDate statDate,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);

    /**
     * 汇总分类维度利用率列表。
     *
     * @param statDate 统计日期
     * @param rangeStart 当日开始时间
     * @param rangeEnd 次日开始时间，作为右开区间上界
     * @return 分类维度聚合结果
     */
    List<StatisticsDaily> summarizeCategoryUtilization(
            @Param("statDate") LocalDate statDate,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);

    /**
     * 汇总全局借还统计总览。
     *
     * @param statDate 统计日期
     * @param rangeStart 当日开始时间
     * @param rangeEnd 次日开始时间，作为右开区间上界
     * @return 全局借还统计汇总行
     */
    StatisticsDaily summarizeGlobalUserBorrow(
            @Param("statDate") LocalDate statDate,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);

    /**
     * 汇总用户借用排行榜底表。
     *
     * @param statDate 统计日期
     * @param rangeStart 当日开始时间
     * @param rangeEnd 次日开始时间，作为右开区间上界
     * @return 用户维度聚合结果
     */
    List<StatisticsDaily> summarizeUserBorrow(
            @Param("statDate") LocalDate statDate,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);

    /**
     * 汇总全局逾期统计。
     *
     * @param statDate 统计日期
     * @param rangeStart 当日开始时间
     * @param rangeEnd 次日开始时间，作为右开区间上界
     * @return 全局逾期统计汇总行
     */
    StatisticsDaily summarizeGlobalOverdue(
            @Param("statDate") LocalDate statDate,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);

    /**
     * 汇总热门时段分布。
     *
     * @param statDate 统计日期
     * @param rangeStart 当日开始时间
     * @param rangeEnd 次日开始时间，作为右开区间上界
     * @return 时段维度聚合结果
     */
    List<StatisticsDaily> summarizeTimeDistribution(
            @Param("statDate") LocalDate statDate,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);

    /**
     * 查询某个统计类型的全局汇总行。
     *
     * @param statDate 统计日期
     * @param statType 统计类型
     * @return 全局汇总行；不存在时返回空
     */
    StatisticsDaily findGlobalRow(
            @Param("statDate") LocalDate statDate,
            @Param("statType") String statType);

    /**
     * 查询某个统计类型、某个对象类型下的全部聚合结果。
     *
     * @param statDate 统计日期
     * @param statType 统计类型
     * @param subjectType 对象类型
     * @return 匹配的聚合行列表
     */
    List<StatisticsDaily> findRowsByDateAndType(
            @Param("statDate") LocalDate statDate,
            @Param("statType") String statType,
            @Param("subjectType") String subjectType);
}
