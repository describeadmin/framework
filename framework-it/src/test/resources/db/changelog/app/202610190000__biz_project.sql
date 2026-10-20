--liquibase formatted sql logicalFilePath:app/202610190000__biz_project.sql

-- =============================================================================
-- 项目（biz_project）
--
-- 由 codegen 生成。启动时由 describeadmin 的迁移层按文件名顺序执行，不需要在任何地方登记。
-- 语法基线：MySQL 5.7 安全子集（CLAUDE.md 3.1）
--
-- ⚠️ biz_project-create 执行过就【不能再改】：加列 / 改列请新建 changelog 文件写 ALTER。
--    biz_project-menu 是 runOnChange，可以改，改了会整体重跑一次，所以语句必须保持幂等。
-- =============================================================================

--changeset app:biz_project-create
-- 审计字段与 BaseEntity 一一对应，请勿手工增删。
CREATE TABLE IF NOT EXISTS biz_project (
  id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  project_name   VARCHAR(128) NOT NULL COMMENT '项目名称',
  project_code   VARCHAR(64)     NULL COMMENT '项目编号',
  owner_dept_id  BIGINT          NULL COMMENT '归口部门ID',
  budget         DECIMAL(18,2)     NULL COMMENT '预算金额',
  start_date     DATE            NULL COMMENT '开始日期',
  status         TINYINT     NOT NULL COMMENT '状态',
  remark         TEXT            NULL COMMENT '备注',
  create_by      BIGINT          NULL COMMENT '创建人',
  create_time    DATETIME        NULL COMMENT '创建时间',
  update_by      BIGINT          NULL COMMENT '更新人',
  update_time    DATETIME        NULL COMMENT '更新时间',
  deleted        TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
  version        INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (id),
  KEY idx_biz_project_project_name (project_name),
  KEY idx_biz_project_project_code (project_code),
  KEY idx_biz_project_owner_dept_id (owner_dept_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci
  COMMENT='项目';

--changeset app:biz_project-menu runOnChange:true
-- =============================================================================
-- 项目 菜单与权限点
--
-- 由 codegen 生成。前端 accessMode = backend，路由完全由 sys_menu 下发，
-- 因此只生成 .vue 文件是不够的：没有菜单行，页面访问不到、侧边栏也没有入口。
--
-- 语法基线：MySQL 5.7 安全子集。幂等靠 INSERT ... SELECT ... WHERE NOT EXISTS，
-- 不用 INSERT IGNORE（依赖唯一索引，而 sys_menu 因逻辑删除未建唯一索引）。
--
-- ⚠️ 执行前确认 component 列的取值与前端实际文件路径一致：
--    'project/index' 对应 frontend 的 src/views/project/index.vue。
--    写错了不会报错，只会在打开页面时静默退化成 404。
-- =============================================================================

-- 业务菜单根目录（已存在则跳过）。component 必须是 BasicLayout，
-- 前端会拿它去 layoutMap 查布局组件，留空会退化成 404 页
INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component,
                      icon, sort, visible, create_time, update_time, deleted, version)
SELECT 0, '业务管理', 'DIR', NULL, '/biz', 'BasicLayout', 'lucide:layers', 10, 1,
       NOW(), NOW(), 0, 0
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu WHERE path = '/biz' AND parent_id = 0 AND deleted = 0
);

-- 列表页
INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component,
                      icon, sort, visible, create_time, update_time, deleted, version)
SELECT m.id, '项目', 'MENU', 'project:list', '/project', 'project/index', 'lucide:table', 1, 1,
       NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.path = '/biz' AND m.parent_id = 0 AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'project:list' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component,
                      icon, sort, visible, create_time, update_time, deleted, version)
SELECT m.id, '新增', 'BUTTON', 'project:add', NULL, NULL, NULL, 1, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'project:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'project:add' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component,
                      icon, sort, visible, create_time, update_time, deleted, version)
SELECT m.id, '编辑', 'BUTTON', 'project:edit', NULL, NULL, NULL, 2, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'project:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'project:edit' AND deleted = 0);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, perm_code, path, component,
                      icon, sort, visible, create_time, update_time, deleted, version)
SELECT m.id, '删除', 'BUTTON', 'project:remove', NULL, NULL, NULL, 3, 1, NOW(), NOW(), 0, 0
FROM sys_menu m
WHERE m.perm_code = 'project:list' AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perm_code = 'project:remove' AND deleted = 0);

-- 授予 ADMIN 角色。
-- ⚠️ 只授 ADMIN：其余角色该看到什么由业主在「角色管理」里决定，
--    生成器替业主决定权限分配是越界的。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r, sys_menu m
WHERE r.role_code = 'ADMIN' AND r.deleted = 0
  AND m.deleted = 0
  AND (m.perm_code LIKE 'project:%' OR m.path = '/biz')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );
