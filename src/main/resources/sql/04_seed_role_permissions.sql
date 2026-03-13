INSERT INTO `role_permission` (`id`, `role_id`, `permission_id`)
SELECT UUID(), r.id, p.id
FROM `role` r
JOIN `permission` p
WHERE r.name = 'USER'
  AND (
    (p.module = 'DEVICE' AND p.code = 'VIEW') OR
    (p.module = 'RESERVATION' AND p.code IN ('VIEW', 'CREATE', 'UPDATE', 'CANCEL', 'BATCH_CREATE')) OR
    (p.module = 'BORROW' AND p.code = 'VIEW') OR
    (p.module = 'OVERDUE' AND p.code = 'VIEW') OR
    (p.module = 'AI_CHAT' AND p.code = 'VIEW')
  );

INSERT INTO `role_permission` (`id`, `role_id`, `permission_id`)
SELECT UUID(), r.id, p.id
FROM `role` r
JOIN `permission` p
WHERE r.name = 'DEVICE_ADMIN'
  AND (
    (p.module = 'DEVICE' AND p.code IN ('VIEW', 'CREATE', 'UPDATE', 'DELETE', 'AUDIT')) OR
    (p.module = 'RESERVATION' AND p.code IN ('VIEW', 'UPDATE', 'CANCEL', 'AUDIT_DEVICE')) OR
    (p.module = 'BORROW' AND p.code IN ('VIEW', 'CREATE', 'UPDATE', 'AUDIT')) OR
    (p.module = 'OVERDUE' AND p.code IN ('VIEW', 'UPDATE', 'AUDIT')) OR
    (p.module = 'AI_CHAT' AND p.code = 'VIEW') OR
    (p.module = 'STATISTICS' AND p.code = 'VIEW')
  );

INSERT INTO `role_permission` (`id`, `role_id`, `permission_id`)
SELECT UUID(), r.id, p.id
FROM `role` r
JOIN `permission` p
WHERE r.name = 'SYSTEM_ADMIN'
  AND (
    (p.module = 'USER_AUTH' AND p.code IN ('VIEW', 'CREATE', 'UPDATE', 'DELETE', 'AUDIT', 'AUTH')) OR
    (p.module = 'DEVICE' AND p.code IN ('VIEW')) OR
    (p.module = 'RESERVATION' AND p.code IN ('VIEW', 'CREATE', 'UPDATE', 'CANCEL', 'AUDIT_SYSTEM', 'PROXY_CREATE', 'BATCH_CREATE')) OR
    (p.module = 'BORROW' AND p.code IN ('VIEW')) OR
    (p.module = 'OVERDUE' AND p.code IN ('VIEW')) OR
    (p.module = 'AI_CHAT' AND p.code = 'VIEW') OR
    (p.module = 'PROMPT_TEMPLATE' AND p.code IN ('VIEW', 'CREATE', 'UPDATE', 'DELETE')) OR
    (p.module = 'STATISTICS' AND p.code = 'VIEW')
  );
