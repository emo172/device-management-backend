INSERT INTO role (id, name, description) VALUES
('role-user', 'USER', '普通用户'),
('role-device-admin', 'DEVICE_ADMIN', '设备管理员'),
('role-system-admin', 'SYSTEM_ADMIN', '系统管理员');

INSERT INTO permission (id, code, name, module, description) VALUES
('perm-user-view', 'VIEW', '查看用户与角色', 'USER_AUTH', '查看用户列表、角色与权限配置'),
('perm-user-update', 'UPDATE', '修改用户与角色', 'USER_AUTH', '修改用户状态、冻结状态、角色配置'),
('perm-user-auth', 'AUTH', '授权角色权限', 'USER_AUTH', '分配用户角色与角色权限');

INSERT INTO role_permission (id, role_id, permission_id) VALUES
('seed-system-view', 'role-system-admin', 'perm-user-view'),
('seed-system-update', 'role-system-admin', 'perm-user-update'),
('seed-system-auth', 'role-system-admin', 'perm-user-auth'),
('seed-device-view', 'role-device-admin', 'perm-user-view');

-- 统计与逾期数据不提供固定种子。
-- 相关集成测试会按场景显式造数，避免不同测试之间共享聚合结果而相互污染。

-- AI 默认 Prompt 模板种子数据。
-- 这些模板为规则降级 provider 提供默认输入来源，避免测试环境依赖真实 LLM 或手工先建模板。
INSERT INTO prompt_template (id, name, code, content, type, description, variables, is_active, version) VALUES
('prompt-intent-default', '默认意图识别模板', 'DEFAULT_INTENT_PROMPT', '请根据用户输入识别意图。', 'INTENT_RECOGNITION', '供 AI 对话意图识别降级使用', '["message"]', 1, '1.0'),
('prompt-info-default', '默认信息提取模板', 'DEFAULT_INFO_PROMPT', '请提取设备、时间和预约编号等结构化信息。', 'INFO_EXTRACTION', '供 AI 对话信息提取降级使用', '["message"]', 1, '1.0'),
('prompt-feedback-default', '默认结果反馈模板', 'DEFAULT_RESULT_PROMPT', '请输出适合用户阅读的结果反馈。', 'RESULT_FEEDBACK', '供 AI 对话结果反馈降级使用', '["intent","message"]', 1, '1.0'),
('prompt-conflict-default', '默认冲突推荐模板', 'DEFAULT_CONFLICT_PROMPT', '当规则冲突时给出人工处理建议。', 'CONFLICT_RECOMMENDATION', '供 AI 对话冲突推荐降级使用', '["message"]', 1, '1.0');
