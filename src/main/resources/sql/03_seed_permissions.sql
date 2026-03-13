INSERT INTO `permission` (`id`, `code`, `name`, `module`, `description`) VALUES
(UUID(), 'VIEW',   '查看用户与角色',     'USER_AUTH',  '查看用户列表、角色与权限配置'),
(UUID(), 'CREATE', '创建用户与角色',     'USER_AUTH',  '创建角色或初始化用户管理资源'),
(UUID(), 'UPDATE', '修改用户与角色',     'USER_AUTH',  '修改用户状态、冻结状态、角色配置'),
(UUID(), 'DELETE', '删除用户与角色配置', 'USER_AUTH',  '删除或清理角色配置'),
(UUID(), 'AUDIT',  '审核用户管理操作',   'USER_AUTH',  '审核用户状态、冻结/解冻等操作'),
(UUID(), 'AUTH',   '授权角色权限',       'USER_AUTH',  '分配用户角色与角色权限'),

(UUID(), 'VIEW',   '查看设备',           'DEVICE',     '查看设备列表、详情与分类'),
(UUID(), 'CREATE', '新增设备',           'DEVICE',     '新增设备与分类'),
(UUID(), 'UPDATE', '修改设备',           'DEVICE',     '修改设备信息与状态'),
(UUID(), 'DELETE', '删除设备',           'DEVICE',     '删除设备或分类'),
(UUID(), 'AUDIT',  '审核设备操作',       'DEVICE',     '审核设备状态流转'),

(UUID(), 'VIEW',         '查看预约',         'RESERVATION','查看预约列表、详情与审批记录'),
(UUID(), 'CREATE',       '创建预约',         'RESERVATION','创建本人预约申请'),
(UUID(), 'UPDATE',       '修改预约',         'RESERVATION','修改预约信息'),
(UUID(), 'CANCEL',       '取消预约',         'RESERVATION','取消预约申请'),
(UUID(), 'AUDIT_DEVICE', '设备初审预约',     'RESERVATION','执行预约第一审，仅设备管理员可用'),
(UUID(), 'AUDIT_SYSTEM', '系统终审预约',     'RESERVATION','执行预约第二审，仅系统管理员可用'),
(UUID(), 'PROXY_CREATE', '代用户创建预约',   'RESERVATION','系统管理员代普通用户创建预约'),
(UUID(), 'BATCH_CREATE', '批量创建预约',     'RESERVATION','普通用户创建本人批量预约或系统管理员创建批量预约批次'),

(UUID(), 'VIEW',   '查看借还',           'BORROW',     '查看借还记录'),
(UUID(), 'CREATE', '生成借还记录',       'BORROW',     '确认借用生成借还记录'),
(UUID(), 'UPDATE', '更新借还记录',       'BORROW',     '确认归还、更新备注'),
(UUID(), 'AUDIT',  '审核借还操作',       'BORROW',     '审核借用与归还流程'),

(UUID(), 'VIEW',   '查看逾期',           'OVERDUE',    '查看逾期记录与统计'),
(UUID(), 'UPDATE', '处理逾期',           'OVERDUE',    '处理逾期记录'),
(UUID(), 'AUDIT',  '审核逾期处理',       'OVERDUE',    '审核逾期处理流程'),

(UUID(), 'VIEW',   '使用AI对话',         'AI_CHAT',         '使用AI对话并查看本人对话历史'),

(UUID(), 'VIEW',   '查看Prompt模板',     'PROMPT_TEMPLATE', '查看Prompt模板列表'),
(UUID(), 'CREATE', '新增Prompt模板',     'PROMPT_TEMPLATE', '新增Prompt模板'),
(UUID(), 'UPDATE', '修改Prompt模板',     'PROMPT_TEMPLATE', '修改Prompt模板'),
(UUID(), 'DELETE', '删除Prompt模板',     'PROMPT_TEMPLATE', '删除Prompt模板'),

(UUID(), 'VIEW',   '查看统计',           'STATISTICS', '查看统计分析结果');

-- 3. 角色权限关联初始化
