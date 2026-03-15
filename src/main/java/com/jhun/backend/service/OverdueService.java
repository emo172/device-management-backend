package com.jhun.backend.service;

import com.jhun.backend.dto.overdue.OverdueRecordPageResponse;
import com.jhun.backend.dto.overdue.OverdueRecordResponse;
import com.jhun.backend.dto.overdue.ProcessOverdueRequest;
import java.time.LocalDateTime;

/**
 * 逾期服务。
 * <p>
 * 负责把借还域的 expected_return_time 检测、冻结策略、逾期处理、通知补发与限制释放编排为一个最小可用闭环。
 */
public interface OverdueService {

    /**
     * 分页查询逾期记录。
     * <p>
     * 普通用户只能查看本人记录，管理角色可以查看全量逾期数据；
     * 该接口同时承接用户端“我的逾期”与管理端逾期工作台列表。
     */
    OverdueRecordPageResponse listOverdueRecords(String userId, String role, int page, int size, String processingStatus);

    /**
     * 查询单条逾期记录详情。
     * <p>
     * 详情接口沿用与列表相同的可见性规则，避免详情成为绕过列表过滤的越权入口。
     */
    OverdueRecordResponse getOverdueRecordDetail(String overdueRecordId, String userId, String role);

    /**
     * 处理逾期记录。
     * <p>
     * 仅允许 DEVICE_ADMIN 写入处理方式、备注、赔偿金额和处理人，
     * 用于把逾期事实从待处理推进到已处理，同时保留审计责任链。
     */
    OverdueRecordResponse processOverdueRecord(String overdueRecordId, String operatorId, String role, ProcessOverdueRequest request);

    /**
     * 执行逾期识别。
     * <p>
     * 基于 borrow_record.expected_return_time 检测新逾期、刷新已逾期时长，
     * 并同步驱动 RESTRICTED / FROZEN 分段冻结策略。
     */
    void detectOverdues(LocalDateTime referenceTime);

    /**
     * 发送尚未完成正式提醒的逾期通知。
     * <p>
     * 该方法对应 C-06，负责把仍处于 OVERDUE 的记录补发为 OVERDUE_WARNING。
     */
    void sendPendingOverdueNotifications(LocalDateTime executeTime);

    /**
     * 自动释放因逾期进入的 RESTRICTED 限制。
     * <p>
     * 该方法对应 C-07，只会自动处理逾期来源的 RESTRICTED 用户，
     * FROZEN 和人工限制账户都不在自动释放范围内。
     */
    void releaseRestrictedUsers(LocalDateTime executeTime);
}
