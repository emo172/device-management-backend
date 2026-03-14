package com.jhun.backend.service;

import com.jhun.backend.dto.borrow.BorrowRecordPageResponse;
import com.jhun.backend.dto.borrow.BorrowRecordResponse;
import com.jhun.backend.dto.borrow.ConfirmBorrowRequest;
import com.jhun.backend.dto.borrow.ConfirmReturnRequest;

/**
 * 借还服务。
 * <p>
 * 负责把预约签到后的实际借出、实际归还与设备状态联动编排为一个事务闭环，避免出现 borrow_record、device、device_status_log 三者不一致。
 */
public interface BorrowService {

    /**
     * 确认借用并生成正式借还记录。
     * <p>
     * 仅允许 {@code DEVICE_ADMIN} 调用，且预约必须已经审批通过并完成正常/超时签到；
     * 该方法需要把 borrow_record 创建、设备状态切换到 {@code BORROWED}、设备状态日志写入作为一个原子动作处理，
     * 否则会出现设备已借出但没有正式借还凭据，或记录已生成但设备仍显示可借的业务裂缝。
     *
     * @param reservationId 预约 ID
     * @param operatorId 当前操作人 ID
     * @param role 当前操作人角色
     * @param request 借用确认参数
     * @return 借还记录响应
     */
    BorrowRecordResponse confirmBorrow(String reservationId, String operatorId, String role, ConfirmBorrowRequest request);

    /**
     * 确认归还并闭合借还记录。
     * <p>
     * 仅允许 {@code DEVICE_ADMIN} 调用，且只有正式借出中的记录才能归还；
     * 该方法负责把借还记录改为 {@code RETURNED}，并把设备状态从 {@code BORROWED} 恢复到 {@code AVAILABLE}，
     * 用于贯彻“设备不能通过手工状态修改直接归还”的规则。
     *
     * @param borrowRecordId 借还记录 ID
     * @param operatorId 当前操作人 ID
     * @param role 当前操作人角色
     * @param request 归还确认参数
     * @return 更新后的借还记录响应
     */
    BorrowRecordResponse confirmReturn(String borrowRecordId, String operatorId, String role, ConfirmReturnRequest request);

    /**
     * 查询借还记录列表。
     * <p>
     * 普通用户只能查看本人借还记录，管理员可从管理视角查看记录；
     * 这样设计是为了同时满足“用户查看本人历史”和“设备管理员管理借还闭环”两个前端承载场景。
     *
     * @param userId 当前登录用户 ID
     * @param role 当前登录角色
     * @param page 页码，从 1 开始
     * @param size 每页大小
     * @param status 可选状态筛选
     * @return 分页记录结果
     */
    BorrowRecordPageResponse listBorrowRecords(String userId, String role, int page, int size, String status);

    /**
     * 查询借还记录详情。
     * <p>
     * 普通用户只能查看本人记录详情，防止越权访问他人的借用轨迹；
     * 管理角色则保留排查设备去向、核对交接记录所需的全量可见能力。
     *
     * @param borrowRecordId 借还记录 ID
     * @param userId 当前登录用户 ID
     * @param role 当前登录角色
     * @return 借还记录详情
     */
    BorrowRecordResponse getBorrowRecordDetail(String borrowRecordId, String userId, String role);
}
