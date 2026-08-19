-- =============================================================================
-- describe-admin RBAC 初始数据
--
-- 用途：本地开发与自动化测试的确定性初始状态（develop_plan.md 5.1 的 seed-job）。
-- 语法基线同 schema-rbac.sql：MySQL 5.7 安全子集。
--
-- ⚠️ 生产环境禁止直接使用本文件。默认账号 admin / admin123 仅供开发与测试；
--    正式部署必须通过初始化流程强制修改初始密码。
--
-- 幂等性：全部使用 INSERT ... SELECT ... WHERE NOT EXISTS，可重复执行。
--         不使用 INSERT IGNORE（依赖唯一索引，而本 schema 因逻辑删除未建唯一索引），
--         也不使用 ON DUPLICATE KEY UPDATE（同理）。
-- =============================================================================

-- 密码为 admin123 的 BCrypt 哈希（已实测 matches 通过）
INSERT INTO sys_user (username, password, nickname, status, create_time, update_time, deleted, version)
SELECT 'admin', '$2a$10$CgwiT6Di8uRu6cwzRgxxJOQLMfHUfrd640xFpmiI3OuU2Bi6/EQMe', '超级管理员', 1, NOW(), NOW(), 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin' AND deleted = 0);

INSERT INTO sys_role (role_code, role_name, sort, create_time, update_time, deleted, version)
SELECT 'ADMIN', '超级管理员', 1, NOW(), NOW(), 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'ADMIN' AND deleted = 0);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND u.deleted = 0
  AND r.role_code = 'ADMIN' AND r.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

-- 演示菜单：系统管理 / 部门管理，用于 5.4 节的结构化测试用例
INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT 0, '系统管理', 'DIR', NULL, '/system', NULL, 'setting', 1, 1, NOW(), NOW(), 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_name = '系统管理' AND parent_id = 0 AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '部门管理', 'MENU', 'system:dept:list', '/system/dept', 'system/dept/index', 'apartment', 1, 1,
       NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.menu_name = '系统管理' AND m.parent_id = 0 AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:dept:list' AND deleted = 0);

-- ADMIN 角色授予全部菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r, sys_menu m
WHERE r.role_code = 'ADMIN' AND r.deleted = 0 AND m.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- 根部门
INSERT INTO sys_dept (parent_id, dept_name, leader, sort, status, create_time, update_time, deleted, version)
SELECT 0, '总部', '管理员', 1, 1, NOW(), NOW(), 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dept WHERE dept_name = '总部' AND parent_id = 0 AND deleted = 0);
