package com.jhun.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 统计日聚合实体。
 * <p>
 * 对应 SQL 真相源中的 {@code statistics_daily} 表，作为统计接口的唯一读取来源。
 * 业务事实数据必须先汇总到该表，再由控制层按总览、利用率、排名等视图输出，
 * 避免每次查询都直接扫描预约、借还和逾期明细表。
 */
@TableName("statistics_daily")
public class StatisticsDaily {

    /** 统计记录主键，统一使用 String UUID。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 统计日期，当前任务按天聚合。 */
    @TableField("stat_date")
    private LocalDate statDate;

    /** 统计类型，如设备利用率、分类利用率、用户借用统计和逾期统计。 */
    @TableField("stat_type")
    private String statType;

    /** 统计粒度，当前实现固定写入 DAY。 */
    private String granularity;

    /** 统计对象类型，如 GLOBAL、DEVICE、USER、CATEGORY、TIME_SLOT。 */
    @TableField("subject_type")
    private String subjectType;

    /** 统计对象值，全局固定为 ALL，其余场景保存设备/分类/用户/时段标识。 */
    @TableField("subject_value")
    private String subjectValue;

    /** 预约总数。 */
    @TableField("total_reservations")
    private Integer totalReservations;

    /** 审批通过预约数。 */
    @TableField("approved_reservations")
    private Integer approvedReservations;

    /** 审批拒绝预约数。 */
    @TableField("rejected_reservations")
    private Integer rejectedReservations;

    /** 用户或系统取消预约数。 */
    @TableField("cancelled_reservations")
    private Integer cancelledReservations;

    /** 未签到或超时等导致的过期预约数。 */
    @TableField("expired_reservations")
    private Integer expiredReservations;

    /** 当日借出总数。 */
    @TableField("total_borrows")
    private Integer totalBorrows;

    /** 当日归还总数。 */
    @TableField("total_returns")
    private Integer totalReturns;

    /** 逾期记录总数。 */
    @TableField("total_overdue")
    private Integer totalOverdue;

    /** 逾期总时长（小时）。 */
    @TableField("total_overdue_hours")
    private Integer totalOverdueHours;

    /** 利用率百分比。 */
    @TableField("utilization_rate")
    private BigDecimal utilizationRate;

    /** 创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }
    public String getStatType() { return statType; }
    public void setStatType(String statType) { this.statType = statType; }
    public String getGranularity() { return granularity; }
    public void setGranularity(String granularity) { this.granularity = granularity; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSubjectValue() { return subjectValue; }
    public void setSubjectValue(String subjectValue) { this.subjectValue = subjectValue; }
    public Integer getTotalReservations() { return totalReservations; }
    public void setTotalReservations(Integer totalReservations) { this.totalReservations = totalReservations; }
    public Integer getApprovedReservations() { return approvedReservations; }
    public void setApprovedReservations(Integer approvedReservations) { this.approvedReservations = approvedReservations; }
    public Integer getRejectedReservations() { return rejectedReservations; }
    public void setRejectedReservations(Integer rejectedReservations) { this.rejectedReservations = rejectedReservations; }
    public Integer getCancelledReservations() { return cancelledReservations; }
    public void setCancelledReservations(Integer cancelledReservations) { this.cancelledReservations = cancelledReservations; }
    public Integer getExpiredReservations() { return expiredReservations; }
    public void setExpiredReservations(Integer expiredReservations) { this.expiredReservations = expiredReservations; }
    public Integer getTotalBorrows() { return totalBorrows; }
    public void setTotalBorrows(Integer totalBorrows) { this.totalBorrows = totalBorrows; }
    public Integer getTotalReturns() { return totalReturns; }
    public void setTotalReturns(Integer totalReturns) { this.totalReturns = totalReturns; }
    public Integer getTotalOverdue() { return totalOverdue; }
    public void setTotalOverdue(Integer totalOverdue) { this.totalOverdue = totalOverdue; }
    public Integer getTotalOverdueHours() { return totalOverdueHours; }
    public void setTotalOverdueHours(Integer totalOverdueHours) { this.totalOverdueHours = totalOverdueHours; }
    public BigDecimal getUtilizationRate() { return utilizationRate; }
    public void setUtilizationRate(BigDecimal utilizationRate) { this.utilizationRate = utilizationRate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
