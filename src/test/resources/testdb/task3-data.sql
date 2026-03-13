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
