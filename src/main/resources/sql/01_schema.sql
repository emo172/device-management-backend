-- ============================================================
-- 智能设备管理系统数据库脚本
-- 数据库名: device_management
-- 字符集: utf8mb4
-- 排序规则: utf8mb4_unicode_ci
-- 存储引擎: InnoDB
-- 主键类型: VARCHAR(36) UUID
-- 创建日期: 2026-02-24
-- 最后修订: 2026-03-12
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `device_management`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `device_management`;

-- ============================================================
-- 删除已有表（按外键依赖逆序删除，确保无冲突）
-- ============================================================
DROP TABLE IF EXISTS `statistics_daily`;
DROP TABLE IF EXISTS `prompt_template`;
DROP TABLE IF EXISTS `chat_history`;
DROP TABLE IF EXISTS `notification_record`;
DROP TABLE IF EXISTS `overdue_record`;
DROP TABLE IF EXISTS `borrow_record`;
DROP TABLE IF EXISTS `device_status_log`;
DROP TABLE IF EXISTS `reservation`;
DROP TABLE IF EXISTS `reservation_batch`;
DROP TABLE IF EXISTS `device`;
DROP TABLE IF EXISTS `device_category`;
DROP TABLE IF EXISTS `password_history`;
DROP TABLE IF EXISTS `role_permission`;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS `permission`;
DROP TABLE IF EXISTS `role`;

-- ============================================================
-- 1. 用户权限模块
-- ============================================================

-- 1.1 角色表（role）
CREATE TABLE `role` (
    `id` VARCHAR(36) NOT NULL COMMENT '角色唯一标识（UUID）',
    `name` VARCHAR(20) NOT NULL COMMENT '角色名称：USER/DEVICE_ADMIN/SYSTEM_ADMIN',
    `description` VARCHAR(255) NULL COMMENT '角色描述',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 1.2 权限表（permission）
CREATE TABLE `permission` (
    `id` VARCHAR(36) NOT NULL COMMENT '权限唯一标识（UUID）',
    `code` VARCHAR(20) NOT NULL COMMENT '权限动作代码，如 VIEW/CREATE/UPDATE/CANCEL/AUDIT_DEVICE/AUDIT_SYSTEM/PROXY_CREATE/BATCH_CREATE/AUTH',
    `name` VARCHAR(50) NOT NULL COMMENT '权限名称',
    `module` VARCHAR(50) NOT NULL COMMENT '所属模块',
    `description` VARCHAR(255) NULL COMMENT '权限描述',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permission_module_code` (`module`, `code`),
    KEY `idx_permission_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- 1.3 用户表（user）
CREATE TABLE `user` (
    `id` VARCHAR(36) NOT NULL COMMENT '用户唯一标识（UUID）',
    `username` VARCHAR(20) NOT NULL COMMENT '用户名，4-20字符',
    `email` VARCHAR(100) NOT NULL COMMENT '邮箱地址',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt加密后的密码',
    `role_id` VARCHAR(36) NOT NULL COMMENT '关联角色ID',
    `real_name` VARCHAR(50) NULL COMMENT '真实姓名',
    `phone` VARCHAR(11) NULL COMMENT '手机号，11位',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '账户状态：1-正常，0-禁用',
    `freeze_status` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '预约冻结状态：NORMAL-正常/FROZEN-冻结/RESTRICTED-限制',
    `freeze_reason` VARCHAR(500) NULL COMMENT '冻结/限制原因',
    `freeze_expire_time` DATETIME NULL COMMENT '限制到期时间',
    `last_login_time` DATETIME NULL COMMENT '最近登录时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_email` (`email`),
    KEY `idx_user_role_id` (`role_id`),
    KEY `idx_user_status` (`status`),
    KEY `idx_user_freeze_status` (`freeze_status`),
    CONSTRAINT `fk_user_role_id` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 1.4 角色权限关联表（role_permission）
CREATE TABLE `role_permission` (
    `id` VARCHAR(36) NOT NULL COMMENT '关联记录唯一标识（UUID）',
    `role_id` VARCHAR(36) NOT NULL COMMENT '角色ID',
    `permission_id` VARCHAR(36) NOT NULL COMMENT '权限ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    KEY `idx_rp_role_id` (`role_id`),
    KEY `idx_rp_permission_id` (`permission_id`),
    CONSTRAINT `fk_rp_role_id` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_rp_permission_id` FOREIGN KEY (`permission_id`) REFERENCES `permission` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- 1.5 密码历史表（password_history）
CREATE TABLE `password_history` (
    `id` VARCHAR(36) NOT NULL COMMENT '记录唯一标识（UUID）',
    `user_id` VARCHAR(36) NOT NULL COMMENT '用户ID',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt加密后的历史密码',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_ph_user_id` (`user_id`),
    CONSTRAINT `fk_ph_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='密码历史表';

-- ============================================================
-- 2. 设备管理模块
-- ============================================================

-- 2.1 设备分类表（device_category）
CREATE TABLE `device_category` (
    `id` VARCHAR(36) NOT NULL COMMENT '分类唯一标识（UUID）',
    `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `parent_id` VARCHAR(36) NULL COMMENT '父分类ID，支持二级分类',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    `description` VARCHAR(255) NULL COMMENT '分类描述',
    `default_approval_mode` VARCHAR(20) NOT NULL DEFAULT 'DEVICE_ONLY' COMMENT '分类默认审批模式：DEVICE_ONLY/DEVICE_THEN_SYSTEM',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_parent_name` (`parent_id`, `name`),
    KEY `idx_category_parent_id` (`parent_id`),
    KEY `idx_category_sort` (`sort_order`),
    KEY `idx_category_default_approval_mode` (`default_approval_mode`),
    CONSTRAINT `chk_category_default_approval_mode` CHECK (`default_approval_mode` IN ('DEVICE_ONLY', 'DEVICE_THEN_SYSTEM')),
    CONSTRAINT `fk_category_parent_id` FOREIGN KEY (`parent_id`) REFERENCES `device_category` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备分类表';

-- 2.2 设备表（device）
CREATE TABLE `device` (
    `id` VARCHAR(36) NOT NULL COMMENT '设备唯一标识（UUID）',
    `name` VARCHAR(100) NOT NULL COMMENT '设备名称',
    `device_number` VARCHAR(50) NOT NULL COMMENT '设备编号，全局唯一',
    `category_id` VARCHAR(36) NOT NULL COMMENT '设备分类ID',
    `status` VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT '设备状态：AVAILABLE-可借/BORROWED-已借出/MAINTENANCE-维修中/DISABLED-停用/DELETED-已删除（业务终态）',
    `approval_mode_override` VARCHAR(20) NULL COMMENT '设备审批模式覆盖：NULL-沿用分类默认/DEVICE_ONLY/DEVICE_THEN_SYSTEM',
    `image_url` VARCHAR(500) NULL COMMENT '设备图片URL',
    `description` TEXT NULL COMMENT '设备说明，支持Markdown',
    `purchase_date` DATE NULL COMMENT '购入日期',
    `location` VARCHAR(100) NULL COMMENT '存放位置',
    `status_change_reason` VARCHAR(500) NULL COMMENT '状态变更原因',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_number` (`device_number`),
    KEY `idx_device_category_id` (`category_id`),
    KEY `idx_device_status` (`status`),
    KEY `idx_device_location` (`location`),
    KEY `idx_device_approval_mode_override` (`approval_mode_override`),
    CONSTRAINT `chk_device_status` CHECK (`status` IN ('AVAILABLE', 'BORROWED', 'MAINTENANCE', 'DISABLED', 'DELETED')),
    CONSTRAINT `chk_device_approval_mode_override` CHECK (`approval_mode_override` IS NULL OR `approval_mode_override` IN ('DEVICE_ONLY', 'DEVICE_THEN_SYSTEM')),
    CONSTRAINT `fk_device_category_id` FOREIGN KEY (`category_id`) REFERENCES `device_category` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备表';

-- 2.3 设备状态变更记录表（device_status_log）
CREATE TABLE `device_status_log` (
    `id` VARCHAR(36) NOT NULL COMMENT '记录唯一标识（UUID）',
    `device_id` VARCHAR(36) NOT NULL COMMENT '设备ID',
    `old_status` VARCHAR(20) NULL COMMENT '变更前状态',
    `new_status` VARCHAR(20) NOT NULL COMMENT '变更后状态',
    `reason` VARCHAR(500) NULL COMMENT '变更原因',
    `operator_id` VARCHAR(36) NULL COMMENT '操作人ID（审计快照字段，仅建索引，不设外键）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
    PRIMARY KEY (`id`),
    KEY `idx_dsl_device_id` (`device_id`),
    KEY `idx_dsl_created_at` (`created_at`),
    KEY `idx_dsl_operator_id` (`operator_id`),
    CONSTRAINT `fk_dsl_device_id` FOREIGN KEY (`device_id`) REFERENCES `device` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备状态变更记录表';

-- ============================================================
-- 3. 预约管理模块
-- ============================================================

-- 3.1 预约批次表（reservation_batch）
CREATE TABLE `reservation_batch` (
    `id` VARCHAR(36) NOT NULL COMMENT '预约批次唯一标识（UUID）',
    `batch_no` VARCHAR(50) NOT NULL COMMENT '批次编号，便于业务检索',
    `created_by` VARCHAR(36) NOT NULL COMMENT '批次创建人ID，可为普通用户或系统管理员',
    `reservation_count` INT NOT NULL DEFAULT 0 COMMENT '批次总预约数',
    `success_count` INT NOT NULL DEFAULT 0 COMMENT '成功预约数',
    `failed_count` INT NOT NULL DEFAULT 0 COMMENT '失败预约数',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '批次状态：PROCESSING/SUCCESS/PARTIAL_SUCCESS/FAILED/CANCELLED',
    `request_snapshot` JSON NULL COMMENT '批量预约入参快照（JSON格式）',
    `result_summary` JSON NULL COMMENT '批量执行结果摘要（JSON格式）',
    `remark` VARCHAR(500) NULL COMMENT '批次备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reservation_batch_no` (`batch_no`),
    KEY `idx_reservation_batch_created_by` (`created_by`),
    KEY `idx_reservation_batch_status` (`status`),
    CONSTRAINT `fk_reservation_batch_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `chk_reservation_batch_status` CHECK (`status` IN ('PROCESSING', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELLED')),
    CONSTRAINT `chk_reservation_batch_count_non_negative` CHECK (`reservation_count` >= 0 AND `success_count` >= 0 AND `failed_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预约批次表';

-- 3.2 预约表（reservation）
CREATE TABLE `reservation` (
    `id` VARCHAR(36) NOT NULL COMMENT '预约唯一标识（UUID）',
    `batch_id` VARCHAR(36) NULL COMMENT '所属预约批次ID，单条预约为空',
    `user_id` VARCHAR(36) NOT NULL COMMENT '实际预约用户ID',
    `created_by` VARCHAR(36) NOT NULL COMMENT '创建人ID；本人预约时与user_id一致，代预约时为系统管理员',
    `reservation_mode` VARCHAR(20) NOT NULL DEFAULT 'SELF' COMMENT '预约模式：SELF/ON_BEHALF',
    `device_id` VARCHAR(36) NOT NULL COMMENT '预约设备ID',
    `start_time` DATETIME NOT NULL COMMENT '预约开始时间',
    `end_time` DATETIME NOT NULL COMMENT '预约结束时间',
    `purpose` VARCHAR(500) NOT NULL COMMENT '预约用途',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING_DEVICE_APPROVAL' COMMENT '预约状态：PENDING_DEVICE_APPROVAL/PENDING_SYSTEM_APPROVAL/PENDING_MANUAL/APPROVED/REJECTED/CANCELLED/EXPIRED',
    `approval_mode_snapshot` VARCHAR(20) NOT NULL COMMENT '审批模式快照：DEVICE_ONLY/DEVICE_THEN_SYSTEM',
    `remark` TEXT NULL COMMENT '预约备注',
    `device_approver_id` VARCHAR(36) NULL COMMENT '设备第一审审核人ID',
    `device_approved_at` DATETIME NULL COMMENT '设备第一审时间',
    `device_approval_remark` VARCHAR(500) NULL COMMENT '设备第一审意见',
    `system_approver_id` VARCHAR(36) NULL COMMENT '系统第二审审核人ID',
    `system_approved_at` DATETIME NULL COMMENT '系统第二审时间',
    `system_approval_remark` VARCHAR(500) NULL COMMENT '系统第二审意见',
    `sign_status` VARCHAR(20) NOT NULL DEFAULT 'NOT_CHECKED_IN' COMMENT '签到状态：NOT_CHECKED_IN/CHECKED_IN/CHECKED_IN_TIMEOUT',
    `checked_in_at` DATETIME NULL COMMENT '签到时间',
    `cancel_reason` VARCHAR(500) NULL COMMENT '取消原因',
    `cancel_time` DATETIME NULL COMMENT '取消时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_reservation_batch_id` (`batch_id`),
    KEY `idx_reservation_user_id` (`user_id`),
    KEY `idx_reservation_created_by` (`created_by`),
    KEY `idx_reservation_device_id` (`device_id`),
    KEY `idx_reservation_status` (`status`),
    KEY `idx_reservation_start_time` (`start_time`),
    KEY `idx_reservation_end_time` (`end_time`),
    KEY `idx_reservation_sign_status` (`sign_status`),
    KEY `idx_reservation_mode` (`reservation_mode`),
    KEY `idx_reservation_approval_mode_snapshot` (`approval_mode_snapshot`),
    KEY `idx_reservation_device_status_time` (`device_id`, `status`, `start_time`, `end_time`),
    CONSTRAINT `fk_reservation_batch_id` FOREIGN KEY (`batch_id`) REFERENCES `reservation_batch` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_reservation_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_reservation_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_reservation_device_id` FOREIGN KEY (`device_id`) REFERENCES `device` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_reservation_device_approver_id` FOREIGN KEY (`device_approver_id`) REFERENCES `user` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_reservation_system_approver_id` FOREIGN KEY (`system_approver_id`) REFERENCES `user` (`id`) ON DELETE SET NULL,
    CONSTRAINT `chk_reservation_time_range` CHECK (`start_time` < `end_time`),
    CONSTRAINT `chk_reservation_status` CHECK (`status` IN ('PENDING_DEVICE_APPROVAL', 'PENDING_SYSTEM_APPROVAL', 'PENDING_MANUAL', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT `chk_reservation_mode` CHECK (`reservation_mode` IN ('SELF', 'ON_BEHALF')),
    CONSTRAINT `chk_reservation_approval_mode_snapshot` CHECK (`approval_mode_snapshot` IN ('DEVICE_ONLY', 'DEVICE_THEN_SYSTEM')),
    CONSTRAINT `chk_reservation_sign_status` CHECK (`sign_status` IN ('NOT_CHECKED_IN', 'CHECKED_IN', 'CHECKED_IN_TIMEOUT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预约表';

-- ============================================================
-- 4. 借还管理模块
-- ============================================================

-- 4.1 借还记录表（borrow_record）
CREATE TABLE `borrow_record` (
    `id` VARCHAR(36) NOT NULL COMMENT '记录唯一标识（UUID）',
    `reservation_id` VARCHAR(36) NOT NULL COMMENT '关联预约ID',
    `device_id` VARCHAR(36) NOT NULL COMMENT '设备ID',
    `user_id` VARCHAR(36) NOT NULL COMMENT '借用用户ID',
    `borrow_time` DATETIME NOT NULL COMMENT '实际借用时间',
    `return_time` DATETIME NULL COMMENT '实际归还时间',
    `expected_return_time` DATETIME NOT NULL COMMENT '预计归还时间（预约结束时间）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'BORROWED' COMMENT '借用状态：BORROWED-借用中/RETURNED-已归还/OVERDUE-已逾期',
    `borrow_check_status` TEXT NULL COMMENT '借用时设备状态检查记录',
    `return_check_status` TEXT NULL COMMENT '归还时设备状态检查记录',
    `remark` TEXT NULL COMMENT '备注说明',
    `operator_id` VARCHAR(36) NOT NULL COMMENT '操作人ID（管理员）',
    `return_operator_id` VARCHAR(36) NULL COMMENT '归还操作人ID（管理员）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_borrow_reservation_id` (`reservation_id`),
    KEY `idx_borrow_device_id` (`device_id`),
    KEY `idx_borrow_user_id` (`user_id`),
    KEY `idx_borrow_status` (`status`),
    KEY `idx_borrow_borrow_time` (`borrow_time`),
    KEY `idx_borrow_return_time` (`return_time`),
    KEY `idx_borrow_status_expected_return` (`status`, `expected_return_time`),
    CONSTRAINT `fk_borrow_reservation_id` FOREIGN KEY (`reservation_id`) REFERENCES `reservation` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_borrow_device_id` FOREIGN KEY (`device_id`) REFERENCES `device` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_borrow_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_borrow_operator_id` FOREIGN KEY (`operator_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_borrow_return_operator_id` FOREIGN KEY (`return_operator_id`) REFERENCES `user` (`id`) ON DELETE SET NULL,
    CONSTRAINT `chk_borrow_expected_return` CHECK (`expected_return_time` >= `borrow_time`),
    CONSTRAINT `chk_borrow_return_time` CHECK (`return_time` IS NULL OR `return_time` >= `borrow_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='借还记录表';

-- ============================================================
-- 5. 逾期管理模块
-- ============================================================

-- 5.1 逾期记录表（overdue_record）
CREATE TABLE `overdue_record` (
    `id` VARCHAR(36) NOT NULL COMMENT '记录唯一标识（UUID）',
    `borrow_record_id` VARCHAR(36) NOT NULL COMMENT '借还记录ID',
    `user_id` VARCHAR(36) NOT NULL COMMENT '逾期用户ID',
    `device_id` VARCHAR(36) NOT NULL COMMENT '设备ID',
    `overdue_hours` INT NOT NULL DEFAULT 0 COMMENT '逾期时长（小时）',
    `overdue_days` INT NOT NULL DEFAULT 0 COMMENT '逾期天数（按统一分段规则计算的展示字段）',
    `processing_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING-待处理/PROCESSED-已处理',
    `processing_method` VARCHAR(20) NULL COMMENT '处理方式：WARNING-警告/COMPENSATION-赔偿/CONTINUE-继续使用',
    `processing_remark` VARCHAR(500) NULL COMMENT '处理备注',
    `processor_id` VARCHAR(36) NULL COMMENT '处理人ID',
    `processing_time` DATETIME NULL COMMENT '处理时间',
    `compensation_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '赔偿金额',
    `notification_sent` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已发送通知',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_overdue_borrow_record_id` (`borrow_record_id`),
    KEY `idx_overdue_user_id` (`user_id`),
    KEY `idx_overdue_device_id` (`device_id`),
    KEY `idx_overdue_processing_status` (`processing_status`),
    KEY `idx_overdue_created_at` (`created_at`),
    CONSTRAINT `fk_overdue_borrow_record_id` FOREIGN KEY (`borrow_record_id`) REFERENCES `borrow_record` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_overdue_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_overdue_device_id` FOREIGN KEY (`device_id`) REFERENCES `device` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_overdue_processor_id` FOREIGN KEY (`processor_id`) REFERENCES `user` (`id`) ON DELETE SET NULL,
    CONSTRAINT `chk_overdue_hours_non_negative` CHECK (`overdue_hours` >= 0),
    CONSTRAINT `chk_overdue_days_non_negative` CHECK (`overdue_days` >= 0),
    CONSTRAINT `chk_overdue_compensation_non_negative` CHECK (`compensation_amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='逾期记录表';

-- ============================================================
-- 6. 消息通知模块
-- ============================================================

-- 6.1 消息通知记录表（notification_record）
CREATE TABLE `notification_record` (
    `id` VARCHAR(36) NOT NULL COMMENT '记录唯一标识（UUID）',
    `user_id` VARCHAR(36) NOT NULL COMMENT '接收用户ID',
    `notification_type` VARCHAR(50) NOT NULL COMMENT '通知类型：VERIFY_CODE/FIRST_APPROVAL_TODO/SECOND_APPROVAL_TODO/APPROVAL_PASSED/APPROVAL_REJECTED/APPROVAL_EXPIRED/RESERVATION_REMINDER/CHECKIN_TIMEOUT_WARNING/BORROW_CONFIRM_WARNING/OVERDUE_WARNING/REVIEW_TIMEOUT_WARNING/RESERVATION_CANCELLED/BATCH_RESERVATION_RESULT/ON_BEHALF_CREATED/PENDING_MANUAL_NOTICE/ACCOUNT_FREEZE_UNFREEZE/DEVICE_MAINTENANCE_NOTICE',
    `channel` VARCHAR(20) NOT NULL DEFAULT 'IN_APP' COMMENT '通知渠道：IN_APP/EMAIL/SMS',
    `title` VARCHAR(200) NOT NULL COMMENT '通知标题',
    `content` TEXT NOT NULL COMMENT '通知内容',
    `template_vars` JSON NULL COMMENT '模板变量（JSON格式）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '发送状态：PENDING-待发送/SENDING-发送中/SUCCESS-发送成功/FAILED-发送失败',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    `error_message` TEXT NULL COMMENT '错误信息',
    `sent_at` DATETIME NULL COMMENT '实际发送时间',
    `read_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读，1-已读（仅IN_APP渠道生效）',
    `read_at` DATETIME NULL COMMENT '已读时间',
    `related_id` VARCHAR(36) NULL COMMENT '关联业务ID（预约ID/逾期ID等）',
    `related_type` VARCHAR(50) NULL COMMENT '关联业务类型：RESERVATION/BORROW/OVERDUE/USER',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_notification_user_id` (`user_id`),
    KEY `idx_notification_type` (`notification_type`),
    KEY `idx_notification_status` (`status`),
    KEY `idx_notification_user_read_created` (`user_id`, `read_flag`, `created_at`),
    KEY `idx_notification_created_at` (`created_at`),
    KEY `idx_notification_related` (`related_type`, `related_id`),
    CONSTRAINT `fk_notification_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `chk_notification_read_flag` CHECK (`read_flag` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息通知记录表';

-- ============================================================
-- 7. AI对话模块
-- ============================================================

-- 7.1 AI对话历史表（chat_history）
CREATE TABLE `chat_history` (
    `id` VARCHAR(36) NOT NULL COMMENT '记录唯一标识（UUID）',
    `user_id` VARCHAR(36) NOT NULL COMMENT '用户ID',
    `session_id` VARCHAR(36) NULL COMMENT '会话ID，用于关联多轮对话',
    `user_input` TEXT NOT NULL COMMENT '用户输入',
    `ai_response` TEXT NULL COMMENT 'AI回复',
    `intent` VARCHAR(50) NULL COMMENT '识别意图：RESERVE-预约/QUERY-查询/CANCEL-取消/HELP-帮助/UNKNOWN-未知',
    `intent_confidence` DECIMAL(3,2) NULL COMMENT '意图识别置信度（0-1）',
    `extracted_info` JSON NULL COMMENT '提取的结构化信息（JSON格式）',
    `device_id` VARCHAR(36) NULL COMMENT '涉及的设备ID',
    `reservation_id` VARCHAR(36) NULL COMMENT '涉及的预约ID',
    `execute_result` VARCHAR(20) NULL COMMENT '执行结果：SUCCESS/FAILED/PENDING',
    `error_message` TEXT NULL COMMENT '错误信息',
    `llm_model` VARCHAR(50) NULL COMMENT '使用的LLM模型',
    `response_time_ms` INT NULL COMMENT '响应时间（毫秒）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_chat_user_id` (`user_id`),
    KEY `idx_chat_session_id` (`session_id`),
    KEY `idx_chat_intent` (`intent`),
    KEY `idx_chat_created_at` (`created_at`),
    KEY `idx_chat_device_id` (`device_id`),
    KEY `idx_chat_reservation_id` (`reservation_id`),
    CONSTRAINT `fk_chat_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_chat_device_id` FOREIGN KEY (`device_id`) REFERENCES `device` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_chat_reservation_id` FOREIGN KEY (`reservation_id`) REFERENCES `reservation` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话历史表';

-- 7.2 Prompt模板表（prompt_template）
CREATE TABLE `prompt_template` (
    `id` VARCHAR(36) NOT NULL COMMENT '模板唯一标识（UUID）',
    `name` VARCHAR(100) NOT NULL COMMENT '模板名称',
    `code` VARCHAR(50) NOT NULL COMMENT '模板代码，用于程序调用',
    `content` TEXT NOT NULL COMMENT '模板内容，支持变量占位符（如{device_name}）',
    `type` VARCHAR(50) NOT NULL COMMENT '模板类型：INTENT_RECOGNITION-意图识别/INFO_EXTRACTION-信息提取/RESULT_FEEDBACK-结果反馈/CONFLICT_RECOMMENDATION-冲突推荐',
    `description` VARCHAR(500) NULL COMMENT '模板描述',
    `variables` JSON NULL COMMENT '模板变量说明（JSON格式）',
    `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-禁用',
    `version` VARCHAR(20) NOT NULL DEFAULT '1.0' COMMENT '模板版本号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_name` (`name`),
    UNIQUE KEY `uk_template_code` (`code`),
    KEY `idx_template_type` (`type`),
    KEY `idx_template_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Prompt模板表';

-- ============================================================
-- 8. 统计分析模块
-- ============================================================

-- 8.1 统计数据聚合表（statistics_daily）
CREATE TABLE `statistics_daily` (
    `id` VARCHAR(36) NOT NULL COMMENT '记录唯一标识（UUID）',
    `stat_date` DATE NOT NULL COMMENT '统计日期',
    `stat_type` VARCHAR(50) NOT NULL COMMENT '统计类型：DEVICE_UTILIZATION-设备利用率/CATEGORY_UTILIZATION-分类利用率/USER_BORROW-用户借用统计/TIME_DISTRIBUTION-时段分布/OVERDUE_STAT-逾期统计',
    `granularity` VARCHAR(20) NOT NULL COMMENT '统计粒度：HOUR/DAY/WEEK/MONTH',
    `subject_type` VARCHAR(20) NOT NULL COMMENT '统计对象类型：GLOBAL/DEVICE/USER/CATEGORY/TIME_SLOT',
    `subject_value` VARCHAR(100) NOT NULL COMMENT '统计对象值（ALL/设备ID/用户ID/分类ID/时段值）',
    `total_reservations` INT NOT NULL DEFAULT 0 COMMENT '预约总数',
    `approved_reservations` INT NOT NULL DEFAULT 0 COMMENT '审核通过数',
    `rejected_reservations` INT NOT NULL DEFAULT 0 COMMENT '审核拒绝数',
    `cancelled_reservations` INT NOT NULL DEFAULT 0 COMMENT '取消数',
    `expired_reservations` INT NOT NULL DEFAULT 0 COMMENT '过期数',
    `total_borrows` INT NOT NULL DEFAULT 0 COMMENT '借用总数',
    `total_returns` INT NOT NULL DEFAULT 0 COMMENT '归还总数',
    `total_overdue` INT NOT NULL DEFAULT 0 COMMENT '逾期数',
    `total_overdue_hours` INT NOT NULL DEFAULT 0 COMMENT '逾期总时长（小时）',
    `utilization_rate` DECIMAL(5,2) NULL COMMENT '利用率（%）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stat_daily` (`stat_date`, `stat_type`, `granularity`, `subject_type`, `subject_value`),
    KEY `idx_stat_date` (`stat_date`),
    KEY `idx_stat_type` (`stat_type`),
    KEY `idx_stat_subject` (`subject_type`, `subject_value`),
    KEY `idx_stat_granularity` (`granularity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统计数据聚合表';

-- ============================================================
-- 初始数据脚本
-- ============================================================

-- 1. 角色数据（3条）
