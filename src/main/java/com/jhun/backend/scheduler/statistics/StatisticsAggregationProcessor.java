package com.jhun.backend.scheduler.statistics;

import com.jhun.backend.service.StatisticsService;
import java.time.LocalDate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * C-08 统计预聚合任务。
 * <p>
 * 该任务每天凌晨 02:30 重算前一天的统计快照，确保管理端统计接口默认读取预聚合结果而不是实时扫明细表。
 * 测试场景不会依赖真实调度触发，而是直接调用 {@link #aggregateForDate(LocalDate)} 完成闭环验证。
 */
@Component
public class StatisticsAggregationProcessor {

    private final StatisticsService statisticsService;

    public StatisticsAggregationProcessor(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * 按 C-08 约定在每日 02:30 聚合上一自然日的数据。
     */
    @Scheduled(cron = "0 30 2 * * ?")
    public void aggregatePreviousDay() {
        aggregateForDate(LocalDate.now().minusDays(1));
    }

    /**
     * 手动执行指定日期的统计聚合。
     *
     * @param statDate 需要重算的统计日期
     */
    public void aggregateForDate(LocalDate statDate) {
        statisticsService.aggregateDailyStatistics(statDate);
    }
}
