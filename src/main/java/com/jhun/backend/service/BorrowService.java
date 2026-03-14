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

    BorrowRecordResponse confirmBorrow(String reservationId, String operatorId, String role, ConfirmBorrowRequest request);

    BorrowRecordResponse confirmReturn(String borrowRecordId, String operatorId, String role, ConfirmReturnRequest request);

    BorrowRecordPageResponse listBorrowRecords(String userId, String role, int page, int size, String status);

    BorrowRecordResponse getBorrowRecordDetail(String borrowRecordId, String userId, String role);
}
