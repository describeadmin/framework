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

-- data_scope = 1（全部）：不在代码里特判 role_code = 'ADMIN'，与"ADMIN 靠种子数据
-- 授予全部菜单"是同一手法——种子数据决定 ADMIN 是超级管理员，不是代码里的特例分支。
INSERT INTO sys_role (role_code, role_name, sort, data_scope, create_time, update_time, deleted, version)
SELECT 'ADMIN', '超级管理员', 1, 1, NOW(), NOW(), 0, 0
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

-- -----------------------------------------------------------------------------
-- 工作台
--
-- accessMode = backend 下，前端的静态路由模块不参与菜单生成，
-- 首页也必须由菜单表下发，否则登录后会落到 404（实测踩过）。
-- -----------------------------------------------------------------------------

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT 0, '工作台', 'DIR', NULL, '/dashboard', 'BasicLayout', 'lucide:layout-dashboard', 0, 1,
       NOW(), NOW(), 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/dashboard' AND parent_id = 0 AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '概览', 'MENU', 'dashboard:view', '/dashboard/workbench', 'dashboard/index',
       'lucide:gauge', 1, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.path = '/dashboard' AND m.parent_id = 0 AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'dashboard:view' AND deleted = 0);

-- -----------------------------------------------------------------------------
-- 系统管理菜单树
--
-- 目录 → 菜单 → 按钮 三层。按钮层不是路由，而是权限点：它的 perm_code 会随
-- /api/auth/me 下发给前端，前端用 v-access:code 控制按钮显隐。
-- 因此「页面里有哪些按钮」和「谁能看到这些按钮」是同一份数据，不会各说各话。
--
-- component 填的是相对 src/views 的路径（不带 .vue），前端 generateRoutesByBackend
-- 会用它到 import.meta.glob 的结果里查找组件。写错了会静默退化成 404 页面，
-- 因此改动此列时必须同步确认对应的 .vue 文件存在。
-- -----------------------------------------------------------------------------

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT 0, '系统管理', 'DIR', NULL, '/system', 'BasicLayout', 'lucide:settings', 1, 1, NOW(), NOW(), 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_name = '系统管理' AND parent_id = 0 AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '用户管理', 'MENU', 'system:user:list', '/system/user', 'system/user/index', 'lucide:users', 1, 1,
       NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.menu_name = '系统管理' AND m.parent_id = 0 AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:user:list' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '角色管理', 'MENU', 'system:role:list', '/system/role', 'system/role/index', 'lucide:shield-check', 2, 1,
       NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.menu_name = '系统管理' AND m.parent_id = 0 AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:role:list' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '菜单管理', 'MENU', 'system:menu:list', '/system/menu', 'system/menu/index', 'lucide:menu', 3, 1,
       NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.menu_name = '系统管理' AND m.parent_id = 0 AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:menu:list' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '部门管理', 'MENU', 'system:dept:list', '/system/dept', 'system/dept/index', 'lucide:building-2', 4, 1,
       NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.menu_name = '系统管理' AND m.parent_id = 0 AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:dept:list' AND deleted = 0);

-- 按钮级权限点。path / component 为 NULL —— 它们不产生路由

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '新增', 'BUTTON', 'system:user:add', NULL, NULL, NULL, 1, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:user:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:user:add' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '编辑', 'BUTTON', 'system:user:edit', NULL, NULL, NULL, 2, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:user:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:user:edit' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '删除', 'BUTTON', 'system:user:remove', NULL, NULL, NULL, 3, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:user:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:user:remove' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '重置密码', 'BUTTON', 'system:user:reset-password', NULL, NULL, NULL, 4, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:user:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:user:reset-password' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '分配角色', 'BUTTON', 'system:user:assign-role', NULL, NULL, NULL, 5, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:user:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:user:assign-role' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '新增', 'BUTTON', 'system:role:add', NULL, NULL, NULL, 1, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:role:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:role:add' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '编辑', 'BUTTON', 'system:role:edit', NULL, NULL, NULL, 2, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:role:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:role:edit' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '删除', 'BUTTON', 'system:role:remove', NULL, NULL, NULL, 3, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:role:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:role:remove' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '分配菜单', 'BUTTON', 'system:role:assign-menu', NULL, NULL, NULL, 4, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:role:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:role:assign-menu' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '分配数据权限', 'BUTTON', 'system:role:assign-dept', NULL, NULL, NULL, 5, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:role:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:role:assign-dept' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '新增', 'BUTTON', 'system:menu:add', NULL, NULL, NULL, 1, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:menu:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:menu:add' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '编辑', 'BUTTON', 'system:menu:edit', NULL, NULL, NULL, 2, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:menu:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:menu:edit' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '删除', 'BUTTON', 'system:menu:remove', NULL, NULL, NULL, 3, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:menu:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:menu:remove' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '新增', 'BUTTON', 'system:dept:add', NULL, NULL, NULL, 1, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:dept:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:dept:add' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '编辑', 'BUTTON', 'system:dept:edit', NULL, NULL, NULL, 2, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:dept:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:dept:edit' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '删除', 'BUTTON', 'system:dept:remove', NULL, NULL, NULL, 3, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:dept:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:dept:remove' AND deleted = 0);

-- 在线用户。前端 @describeadmin/system-ui 的 system/online/index 页面已交付。

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '在线用户', 'MENU', 'system:online:list', '/system/online', 'system/online/index', 'lucide:monitor-dot', 5, 1,
       NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.menu_name = '系统管理' AND m.parent_id = 0 AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:online:list' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '强制下线', 'BUTTON', 'system:online:remove', NULL, NULL, NULL, 1, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:online:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:online:remove' AND deleted = 0);

-- -----------------------------------------------------------------------------
-- 字典管理。字典类型与字典数据共用 system:dict 前缀（同一个管理页面的两个面板），
-- 见 SysDictTypeController/SysDictDataController 都覆写的 permPrefix()。
-- -----------------------------------------------------------------------------

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '字典管理', 'MENU', 'system:dict:list', '/system/dict', 'system/dict/index', 'lucide:book-open', 6, 1,
       NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.menu_name = '系统管理' AND m.parent_id = 0 AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:dict:list' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '新增', 'BUTTON', 'system:dict:add', NULL, NULL, NULL, 1, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:dict:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:dict:add' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '编辑', 'BUTTON', 'system:dict:edit', NULL, NULL, NULL, 2, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:dict:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:dict:edit' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '删除', 'BUTTON', 'system:dict:remove', NULL, NULL, NULL, 3, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:dict:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:dict:remove' AND deleted = 0);

-- -----------------------------------------------------------------------------
-- 参数配置
-- -----------------------------------------------------------------------------

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '参数配置', 'MENU', 'system:config:list', '/system/config', 'system/config/index', 'lucide:sliders-horizontal',
       7, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.menu_name = '系统管理' AND m.parent_id = 0 AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:config:list' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '新增', 'BUTTON', 'system:config:add', NULL, NULL, NULL, 1, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:config:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:config:add' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '编辑', 'BUTTON', 'system:config:edit', NULL, NULL, NULL, 2, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:config:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:config:edit' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '删除', 'BUTTON', 'system:config:remove', NULL, NULL, NULL, 3, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:config:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:config:remove' AND deleted = 0);

-- -----------------------------------------------------------------------------
-- 操作日志。只有"删除"一个按钮权限点——清空复用它，不单独开一个权限对象
-- （见 SysOperLogController 的类注释）。
-- -----------------------------------------------------------------------------

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '操作日志', 'MENU', 'system:oper-log:list', '/system/oper-log', 'system/oper-log/index',
       'lucide:scroll-text', 8, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.menu_name = '系统管理' AND m.parent_id = 0 AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:oper-log:list' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component, icon, sort, visible,
                      create_time, update_time, deleted, version)
SELECT m.id, '删除', 'BUTTON', 'system:oper-log:remove', NULL, NULL, NULL, 1, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'system:oper-log:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'system:oper-log:remove' AND deleted = 0);

-- ADMIN 角色授予全部菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r, sys_menu m
WHERE r.role_code = 'ADMIN' AND r.deleted = 0 AND m.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- 根部门。ancestors 显式写空串——顶级部门没有祖先，不依赖列默认值，
-- 与本文件其余 INSERT 逐列列全的风格一致。
INSERT INTO sys_dept (parent_id, dept_name, leader, sort, status, ancestors,
                      create_time, update_time, deleted, version)
SELECT 0, '总部', '管理员', 1, 1, '', NOW(), NOW(), 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dept WHERE dept_name = '总部' AND parent_id = 0 AND deleted = 0);
