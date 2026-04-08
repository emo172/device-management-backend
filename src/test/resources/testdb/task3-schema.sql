CREATE TABLE role (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_name UNIQUE (name)
);

CREATE TABLE permission (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(50) NOT NULL,
    module VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_permission_module_code UNIQUE (module, code)
);

CREATE TABLE `user` (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    username VARCHAR(20) NOT NULL,
    email VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role_id VARCHAR(36) NOT NULL,
    real_name VARCHAR(50),
    phone VARCHAR(11),
    status TINYINT NOT NULL DEFAULT 1,
    freeze_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    freeze_reason VARCHAR(500),
    freeze_expire_time TIMESTAMP,
    last_login_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_username UNIQUE (username),
    CONSTRAINT uk_user_email UNIQUE (email),
    CONSTRAINT fk_user_role_id FOREIGN KEY (role_id) REFERENCES role (id)
);

CREATE TABLE role_permission (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    role_id VARCHAR(36) NOT NULL,
    permission_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id),
    CONSTRAINT fk_rp_role_id FOREIGN KEY (role_id) REFERENCES role (id),
    CONSTRAINT fk_rp_permission_id FOREIGN KEY (permission_id) REFERENCES permission (id)
);

CREATE TABLE password_history (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ph_user_id FOREIGN KEY (user_id) REFERENCES `user` (id)
);

CREATE TABLE notification_record (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    template_vars VARCHAR(2000),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(2000),
    sent_at TIMESTAMP,
    read_flag TINYINT NOT NULL DEFAULT 0,
    read_at TIMESTAMP,
    related_id VARCHAR(36),
    related_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user_id FOREIGN KEY (user_id) REFERENCES `user` (id)
);

CREATE TABLE device_category (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    parent_id VARCHAR(36),
    sort_order INT NOT NULL DEFAULT 0,
    description VARCHAR(255),
    default_approval_mode VARCHAR(20) NOT NULL DEFAULT 'DEVICE_ONLY',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_category_parent_name UNIQUE (parent_id, name),
    CONSTRAINT fk_category_parent_id FOREIGN KEY (parent_id) REFERENCES device_category (id)
);

CREATE TABLE device (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    device_number VARCHAR(50) NOT NULL,
    category_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    approval_mode_override VARCHAR(20),
    image_url VARCHAR(500),
    description VARCHAR(2000),
    purchase_date DATE,
    location VARCHAR(100),
    status_change_reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_device_number UNIQUE (device_number),
    CONSTRAINT fk_device_category_id FOREIGN KEY (category_id) REFERENCES device_category (id)
);

CREATE TABLE device_status_log (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    device_id VARCHAR(36) NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    operator_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dsl_device_id FOREIGN KEY (device_id) REFERENCES device (id)
);

CREATE TABLE reservation_batch (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    batch_no VARCHAR(50),
    created_by VARCHAR(36),
    reservation_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    failed_count INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'PROCESSING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reservation (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    batch_id VARCHAR(36),
    user_id VARCHAR(36) NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    reservation_mode VARCHAR(20) NOT NULL DEFAULT 'SELF',
    device_id VARCHAR(36),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    purpose VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_DEVICE_APPROVAL',
    approval_mode_snapshot VARCHAR(20) NOT NULL,
    remark VARCHAR(1000),
    device_approver_id VARCHAR(36),
    device_approved_at TIMESTAMP,
    device_approval_remark VARCHAR(500),
    system_approver_id VARCHAR(36),
    system_approved_at TIMESTAMP,
    system_approval_remark VARCHAR(500),
    sign_status VARCHAR(20) NOT NULL DEFAULT 'NOT_CHECKED_IN',
    checked_in_at TIMESTAMP,
    cancel_reason VARCHAR(500),
    cancel_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservation_user_id FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_reservation_created_by FOREIGN KEY (created_by) REFERENCES `user` (id),
    CONSTRAINT fk_reservation_device_id FOREIGN KEY (device_id) REFERENCES device (id)
);

CREATE TABLE reservation_device (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    reservation_id VARCHAR(36) NOT NULL,
    device_id VARCHAR(36) NOT NULL,
    device_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_reservation_device_reservation_device UNIQUE (reservation_id, device_id),
    CONSTRAINT uk_reservation_device_reservation_order UNIQUE (reservation_id, device_order),
    CONSTRAINT fk_reservation_device_reservation_id FOREIGN KEY (reservation_id) REFERENCES reservation (id) ON DELETE CASCADE,
    CONSTRAINT fk_reservation_device_device_id FOREIGN KEY (device_id) REFERENCES device (id)
);

CREATE TABLE borrow_record (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    reservation_id VARCHAR(36) NOT NULL,
    device_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    borrow_time TIMESTAMP NOT NULL,
    return_time TIMESTAMP,
    expected_return_time TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'BORROWED',
    borrow_check_status CLOB,
    return_check_status CLOB,
    remark CLOB,
    operator_id VARCHAR(36) NOT NULL,
    return_operator_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- 测试库与正式库保持一致：多设备预约允许同一 reservation_id 下存在多条 borrow_record，但同一设备只能有一条。
    CONSTRAINT uk_borrow_reservation_device UNIQUE (reservation_id, device_id),
    CONSTRAINT fk_borrow_reservation_id FOREIGN KEY (reservation_id) REFERENCES reservation (id),
    CONSTRAINT fk_borrow_device_id FOREIGN KEY (device_id) REFERENCES device (id),
    CONSTRAINT fk_borrow_user_id FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_borrow_operator_id FOREIGN KEY (operator_id) REFERENCES `user` (id),
    CONSTRAINT fk_borrow_return_operator_id FOREIGN KEY (return_operator_id) REFERENCES `user` (id)
);

-- 逾期记录表。
-- 统计聚合需要直接读取逾期条目，因此测试库补齐正式主字段与唯一约束，
-- 以覆盖“业务事实数据 -> 逾期统计 -> 统计接口”的闭环。
CREATE TABLE overdue_record (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    borrow_record_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    device_id VARCHAR(36) NOT NULL,
    overdue_hours INT NOT NULL DEFAULT 0,
    overdue_days INT NOT NULL DEFAULT 0,
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    processing_method VARCHAR(20),
    processing_remark VARCHAR(500),
    processor_id VARCHAR(36),
    processing_time TIMESTAMP,
    compensation_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    notification_sent TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_overdue_borrow_record_id UNIQUE (borrow_record_id),
    CONSTRAINT fk_overdue_borrow_record_id FOREIGN KEY (borrow_record_id) REFERENCES borrow_record (id),
    CONSTRAINT fk_overdue_user_id FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_overdue_device_id FOREIGN KEY (device_id) REFERENCES device (id),
    CONSTRAINT fk_overdue_processor_id FOREIGN KEY (processor_id) REFERENCES `user` (id)
);

-- 统计日聚合表。
-- 该表是统计接口的唯一读取来源，测试环境必须保持和正式口径一致，
-- 包含统计类型、粒度、对象类型和值，以及预约/借还/逾期相关的核心指标字段。
CREATE TABLE statistics_daily (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    stat_date DATE NOT NULL,
    stat_type VARCHAR(50) NOT NULL,
    granularity VARCHAR(20) NOT NULL,
    subject_type VARCHAR(20) NOT NULL,
    subject_value VARCHAR(100) NOT NULL,
    total_reservations INT NOT NULL DEFAULT 0,
    approved_reservations INT NOT NULL DEFAULT 0,
    rejected_reservations INT NOT NULL DEFAULT 0,
    cancelled_reservations INT NOT NULL DEFAULT 0,
    expired_reservations INT NOT NULL DEFAULT 0,
    total_borrows INT NOT NULL DEFAULT 0,
    total_returns INT NOT NULL DEFAULT 0,
    total_overdue INT NOT NULL DEFAULT 0,
    total_overdue_hours INT NOT NULL DEFAULT 0,
    utilization_rate DECIMAL(5, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stat_daily UNIQUE (stat_date, stat_type, granularity, subject_type, subject_value)
);

-- AI 对话历史表。
-- 测试环境需要覆盖 AI 对话写入、本人历史查询和详情读取，因此补齐最小字段与外键约束。
CREATE TABLE chat_history (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    session_id VARCHAR(36),
    user_input CLOB NOT NULL,
    ai_response CLOB,
    intent VARCHAR(50),
    intent_confidence DECIMAL(3, 2),
    extracted_info CLOB,
    device_id VARCHAR(36),
    reservation_id VARCHAR(36),
    execute_result VARCHAR(20),
    error_message CLOB,
    llm_model VARCHAR(50),
    response_time_ms INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_history_user_id FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_chat_history_device_id FOREIGN KEY (device_id) REFERENCES device (id),
    CONSTRAINT fk_chat_history_reservation_id FOREIGN KEY (reservation_id) REFERENCES reservation (id)
);

-- Prompt 模板表。
-- 测试环境仅需支撑系统管理员的模板管理与 AI 服务的降级模板读取，因此保留正式表主字段。
CREATE TABLE prompt_template (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    content CLOB NOT NULL,
    type VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    variables CLOB,
    is_active TINYINT NOT NULL DEFAULT 1,
    version VARCHAR(20) NOT NULL DEFAULT '1.0',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_prompt_template_name UNIQUE (name),
    CONSTRAINT uk_prompt_template_code UNIQUE (code)
);
