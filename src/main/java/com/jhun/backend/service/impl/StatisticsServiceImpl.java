package com.jhun.backend.service.impl;

import com.jhun.backend.dto.statistics.BorrowStatisticsResponse;
import com.jhun.backend.dto.statistics.CategoryUtilizationResponse;
import com.jhun.backend.dto.statistics.DeviceRankingResponse;
import com.jhun.backend.dto.statistics.DeviceUtilizationResponse;
import com.jhun.backend.dto.statistics.OverdueStatisticsResponse;
import com.jhun.backend.dto.statistics.StatisticsOverviewResponse;
import com.jhun.backend.dto.statistics.TimeSlotStatisticsResponse;
import com.jhun.backend.dto.statistics.UserRankingResponse;
import com.jhun.backend.entity.Device;
import com.jhun.backend.entity.DeviceCategory;
import com.jhun.backend.entity.StatisticsDaily;
import com.jhun.backend.entity.User;
import com.jhun.backend.mapper.DeviceCategoryMapper;
import com.jhun.backend.mapper.DeviceMapper;
import com.jhun.backend.mapper.StatisticsDailyMapper;
import com.jhun.backend.mapper.UserMapper;
import com.jhun.backend.service.StatisticsService;
import com.jhun.backend.util.UuidUtil;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 统计分析服务实现。
 * <p>
 * 当前阶段优先遵循“统计查询只读 {@code statistics_daily}”的边界：
 * 聚合任务单独负责从预约、借还和逾期明细生成日聚合，接口读取层只做名称补全、排序和兜底零值处理。
 */
@Service
public class StatisticsServiceImpl implements StatisticsService {

    private static final String GLOBAL_SUBJECT_TYPE = "GLOBAL";
    private static final String GLOBAL_SUBJECT_VALUE = "ALL";

    private final StatisticsDailyMapper statisticsDailyMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceCategoryMapper deviceCategoryMapper;
    private final UserMapper userMapper;

    public StatisticsServiceImpl(
            StatisticsDailyMapper statisticsDailyMapper,
            DeviceMapper deviceMapper,
            DeviceCategoryMapper deviceCategoryMapper,
            UserMapper userMapper) {
        this.statisticsDailyMapper = statisticsDailyMapper;
        this.deviceMapper = deviceMapper;
        this.deviceCategoryMapper = deviceCategoryMapper;
        this.userMapper = userMapper;
    }

    @Override
    /**
     * 从三类全局聚合行中组装总览卡片。
     * 这里不再回查事实表，避免读取层绕过 `statistics_daily` 的预聚合边界。
     */
    public StatisticsOverviewResponse getOverview(LocalDate statDate) {
        StatisticsDaily deviceOverview = statisticsDailyMapper.findGlobalRow(statDate, "DEVICE_UTILIZATION");
        StatisticsDaily borrowOverview = statisticsDailyMapper.findGlobalRow(statDate, "USER_BORROW");
        StatisticsDaily overdueOverview = statisticsDailyMapper.findGlobalRow(statDate, "OVERDUE_STAT");

        return new StatisticsOverviewResponse(
                statDate,
                intValue(deviceOverview == null ? null : deviceOverview.getTotalReservations()),
                intValue(deviceOverview == null ? null : deviceOverview.getApprovedReservations()),
                intValue(deviceOverview == null ? null : deviceOverview.getRejectedReservations()),
                intValue(deviceOverview == null ? null : deviceOverview.getCancelledReservations()),
                intValue(deviceOverview == null ? null : deviceOverview.getExpiredReservations()),
                intValue(borrowOverview == null ? null : borrowOverview.getTotalBorrows()),
                intValue(borrowOverview == null ? null : borrowOverview.getTotalReturns()),
                intValue(overdueOverview == null ? null : overdueOverview.getTotalOverdue()),
                intValue(overdueOverview == null ? null : overdueOverview.getTotalOverdueHours()),
                decimalValue(deviceOverview == null ? null : deviceOverview.getUtilizationRate()));
    }

    @Override
    /**
     * 查询设备维度利用率，并补齐设备名与分类名供前端直接展示。
     */
    public List<DeviceUtilizationResponse> listDeviceUtilization(LocalDate statDate) {
        return statisticsDailyMapper.findRowsByDateAndType(statDate, "DEVICE_UTILIZATION", "DEVICE").stream()
                .sorted(Comparator.comparing(StatisticsDaily::getUtilizationRate, this::compareDecimal).reversed()
                        .thenComparing(StatisticsDaily::getSubjectValue))
                .map(row -> {
                    Device device = deviceMapper.selectById(row.getSubjectValue());
                    DeviceCategory category = device == null ? null : deviceCategoryMapper.selectById(device.getCategoryId());
                    return new DeviceUtilizationResponse(
                            row.getSubjectValue(),
                            device == null ? row.getSubjectValue() : device.getName(),
                            device == null ? null : device.getCategoryId(),
                            category == null ? null : category.getName(),
                            intValue(row.getTotalReservations()),
                            intValue(row.getTotalBorrows()),
                            intValue(row.getTotalReturns()),
                            intValue(row.getTotalOverdue()),
                            decimalValue(row.getUtilizationRate()));
                })
                .toList();
    }

    @Override
    /**
     * 查询分类维度利用率，并把分类 ID 转成可读名称。
     */
    public List<CategoryUtilizationResponse> listCategoryUtilization(LocalDate statDate) {
        return statisticsDailyMapper.findRowsByDateAndType(statDate, "CATEGORY_UTILIZATION", "CATEGORY").stream()
                .sorted(Comparator.comparing(StatisticsDaily::getUtilizationRate, this::compareDecimal).reversed()
                        .thenComparing(StatisticsDaily::getSubjectValue))
                .map(row -> {
                    DeviceCategory category = deviceCategoryMapper.selectById(row.getSubjectValue());
                    return new CategoryUtilizationResponse(
                            row.getSubjectValue(),
                            category == null ? row.getSubjectValue() : category.getName(),
                            intValue(row.getTotalReservations()),
                            intValue(row.getTotalBorrows()),
                            intValue(row.getTotalReturns()),
                            intValue(row.getTotalOverdue()),
                            decimalValue(row.getUtilizationRate()));
                })
                .toList();
    }

    @Override
    /**
     * 读取借还统计卡片。
     */
    public BorrowStatisticsResponse getBorrowStatistics(LocalDate statDate) {
        StatisticsDaily row = statisticsDailyMapper.findGlobalRow(statDate, "USER_BORROW");
        return new BorrowStatisticsResponse(
                statDate,
                intValue(row == null ? null : row.getTotalBorrows()),
                intValue(row == null ? null : row.getTotalReturns()));
    }

    @Override
    /**
     * 读取逾期统计卡片。
     */
    public OverdueStatisticsResponse getOverdueStatistics(LocalDate statDate) {
        StatisticsDaily row = statisticsDailyMapper.findGlobalRow(statDate, "OVERDUE_STAT");
        return new OverdueStatisticsResponse(
                statDate,
                intValue(row == null ? null : row.getTotalOverdue()),
                intValue(row == null ? null : row.getTotalOverdueHours()));
    }

    @Override
    /**
     * 查询热门时段，默认按预约总数倒序输出。
     */
    public List<TimeSlotStatisticsResponse> listHotTimeSlots(LocalDate statDate) {
        return statisticsDailyMapper.findRowsByDateAndType(statDate, "TIME_DISTRIBUTION", "TIME_SLOT").stream()
                .sorted(Comparator.comparing(StatisticsDaily::getTotalReservations).reversed()
                        .thenComparing(StatisticsDaily::getSubjectValue))
                .map(row -> new TimeSlotStatisticsResponse(
                        row.getSubjectValue(),
                        intValue(row.getTotalReservations()),
                        intValue(row.getApprovedReservations())))
                .toList();
    }

    @Override
    /**
     * 查询设备排行榜。
     * 借出次数是主排序键，利用率为次排序键，保证“热门设备”优先体现真实借出活跃度。
     */
    public List<DeviceRankingResponse> listDeviceRanking(LocalDate statDate) {
        return statisticsDailyMapper.findRowsByDateAndType(statDate, "DEVICE_UTILIZATION", "DEVICE").stream()
                .sorted(Comparator.comparing((StatisticsDaily row) -> intValue(row.getTotalBorrows())).reversed()
                        .thenComparing((StatisticsDaily row) -> decimalValue(row.getUtilizationRate()), Comparator.reverseOrder())
                        .thenComparing(StatisticsDaily::getSubjectValue))
                .limit(10)
                .map(row -> {
                    Device device = deviceMapper.selectById(row.getSubjectValue());
                    return new DeviceRankingResponse(
                            row.getSubjectValue(),
                            device == null ? row.getSubjectValue() : device.getName(),
                            intValue(row.getTotalBorrows()),
                            decimalValue(row.getUtilizationRate()));
                })
                .toList();
    }

    @Override
    /**
     * 查询用户借用排行榜，并回填真实用户名与姓名。
     */
    public List<UserRankingResponse> listUserRanking(LocalDate statDate) {
        return statisticsDailyMapper.findRowsByDateAndType(statDate, "USER_BORROW", "USER").stream()
                .sorted(Comparator.comparing(StatisticsDaily::getTotalBorrows).reversed()
                        .thenComparing(StatisticsDaily::getSubjectValue))
                .limit(10)
                .map(row -> {
                    User user = userMapper.selectById(row.getSubjectValue());
                    return new UserRankingResponse(
                            row.getSubjectValue(),
                            user == null ? row.getSubjectValue() : user.getUsername(),
                            user == null ? null : user.getRealName(),
                            intValue(row.getTotalBorrows()));
                })
                .toList();
    }

    @Override
    @Transactional
    /**
     * 按自然日重算统计聚合结果。
     * 统一使用左闭右开时间区间，避免 `DATE(column)` 造成索引失效，
     * 同时确保“前一天借出、当天归还”会正确计入当天归还数。
     */
    public void aggregateDailyStatistics(LocalDate statDate) {
        LocalDateTime rangeStart = statDate.atStartOfDay();
        LocalDateTime rangeEnd = statDate.plusDays(1).atStartOfDay();

        statisticsDailyMapper.deleteByStatDate(statDate);

        saveIfPresent(statisticsDailyMapper.summarizeGlobalDeviceUtilization(statDate, rangeStart, rangeEnd));
        saveRows(statisticsDailyMapper.summarizeDeviceUtilization(statDate, rangeStart, rangeEnd));
        saveIfPresent(statisticsDailyMapper.summarizeGlobalCategoryUtilization(statDate, rangeStart, rangeEnd));
        saveRows(statisticsDailyMapper.summarizeCategoryUtilization(statDate, rangeStart, rangeEnd));
        saveIfPresent(statisticsDailyMapper.summarizeGlobalUserBorrow(statDate, rangeStart, rangeEnd));
        saveRows(statisticsDailyMapper.summarizeUserBorrow(statDate, rangeStart, rangeEnd));
        saveIfPresent(statisticsDailyMapper.summarizeGlobalOverdue(statDate, rangeStart, rangeEnd));
        saveRows(statisticsDailyMapper.summarizeTimeDistribution(statDate, rangeStart, rangeEnd));
    }

    /**
     * 聚合查询返回的记录还未带主键与时间戳，这里统一补齐正式入库字段。
     */
    private void saveRows(List<StatisticsDaily> rows) {
        for (StatisticsDaily row : rows) {
            saveIfPresent(row);
        }
    }

    /*
     * 聚合 SQL 只负责生成业务指标，主键与默认粒度由服务层统一回填，
     * 这样可以避免同一套 SQL 因主键生成策略不同而掺入数据库方言逻辑。
     */
    private void saveIfPresent(StatisticsDaily row) {
        if (row == null) {
            return;
        }
        row.setId(UuidUtil.randomUuid());
        if (row.getGranularity() == null) {
            row.setGranularity("DAY");
        }
        statisticsDailyMapper.insert(row);
    }

    private Integer intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal decimalValue(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }

    private int compareDecimal(BigDecimal left, BigDecimal right) {
        BigDecimal safeLeft = left == null ? BigDecimal.ZERO : left;
        BigDecimal safeRight = right == null ? BigDecimal.ZERO : right;
        return safeLeft.compareTo(safeRight);
    }
}
