-- =============================================================================
-- describe-admin RBAC 基础表结构
--
-- 语法基线：MySQL 5.7 的安全子集（develop_plan.md 2.3.1）
--
-- 本文件遵守的红线，改动时请逐条核对：
--   ✗ 不使用窗口函数 / CTE / 函数索引 / 不可见列 / 生成列 / JSON_TABLE
--   ✗ 不使用 TIMESTAMP（2038 上限 + 自动更新语义在各库不一致），统一用 DATETIME
--   ✗ 不依赖 CHECK 约束的实际生效行为（5.7 会解析但不强制）
--   ✗ 不依赖服务器默认字符集
--   ✓ 显式声明 CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
--   ✓ 索引键长度按 5.7 保守限制设计（utf8mb4 下单列索引前缀不超过 191 字符）
--   ✓ 主键为 BIGINT AUTO_INCREMENT，与全局 IdType.AUTO 一致
--
-- 审计字段与 BaseEntity 一一对应，业务表同样应包含这 6 个字段。
-- =============================================================================

CREATE TABLE IF NOT EXISTS sys_user (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username     VARCHAR(64)  NOT NULL                COMMENT '登录名',
  password     VARCHAR(100) NOT NULL                COMMENT '密码（BCrypt 哈希）',
  nickname     VARCHAR(64)      NULL                COMMENT '昵称',
  status       TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
  create_by    BIGINT           NULL                COMMENT '创建人',
  create_time  DATETIME         NULL                COMMENT '创建时间',
  update_by    BIGINT           NULL                COMMENT '更新人',
  update_time  DATETIME         NULL                COMMENT '更新时间',
  deleted      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删 1已删',
  version      INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (id),
  -- 注意：逻辑删除下不能对 username 建唯一索引，否则删除后无法复用同名账号。
  -- 唯一性由应用层在「未删除」范围内校验。
  KEY idx_sys_user_username (username)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci
  COMMENT='用户';

CREATE TABLE IF NOT EXISTS sys_role (
  id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_code    VARCHAR(64) NOT NULL                COMMENT '角色标识',
  role_name    VARCHAR(64) NOT NULL                COMMENT '角色名称',
  sort         INT         NOT NULL DEFAULT 0      COMMENT '排序',
  create_by    BIGINT          NULL                COMMENT '创建人',
  create_time  DATETIME        NULL                COMMENT '创建时间',
  update_by    BIGINT          NULL                COMMENT '更新人',
  update_time  DATETIME        NULL                COMMENT '更新时间',
  deleted      TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  version      INT         NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (id),
  KEY idx_sys_role_code (role_code)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci
  COMMENT='角色';

CREATE TABLE IF NOT EXISTS sys_menu (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  parent_id    BIGINT       NOT NULL DEFAULT 0      COMMENT '父菜单ID，0为根',
  menu_name    VARCHAR(64)  NOT NULL                COMMENT '菜单名称',
  menu_type    VARCHAR(16)  NOT NULL                COMMENT '类型：DIR目录 MENU菜单 BUTTON按钮',
  perm_code    VARCHAR(128)     NULL                COMMENT '权限标识，如 system:dept:add',
  path         VARCHAR(191)     NULL                COMMENT '路由路径',
  component    VARCHAR(191)     NULL                COMMENT '前端组件路径',
  icon         VARCHAR(64)      NULL                COMMENT '图标',
  sort         INT          NOT NULL DEFAULT 0      COMMENT '排序',
  visible      TINYINT      NOT NULL DEFAULT 1      COMMENT '是否显示：1是 0否',
  create_by    BIGINT           NULL                COMMENT '创建人',
  create_time  DATETIME         NULL                COMMENT '创建时间',
  update_by    BIGINT           NULL                COMMENT '更新人',
  update_time  DATETIME         NULL                COMMENT '更新时间',
  deleted      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  version      INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (id),
  KEY idx_sys_menu_parent (parent_id),
  -- perm_code 长度 128，utf8mb4 下索引字节数 512，远低于 5.7 的 767 字节单列上限
  KEY idx_sys_menu_perm (perm_code)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci
  COMMENT='菜单与权限';

-- 关联表不设审计字段与逻辑删除：授权关系是「重建」语义而非「修改」语义，
-- 每次授权直接物理删除旧关系再插入新关系，保留软删记录只会让查询变复杂。
CREATE TABLE IF NOT EXISTS sys_user_role (
  user_id BIGINT NOT NULL COMMENT '用户ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  PRIMARY KEY (user_id, role_id),
  KEY idx_sys_user_role_role (role_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci
  COMMENT='用户角色关联';

CREATE TABLE IF NOT EXISTS sys_role_menu (
  role_id BIGINT NOT NULL COMMENT '角色ID',
  menu_id BIGINT NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (role_id, menu_id),
  KEY idx_sys_role_menu_menu (menu_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci
  COMMENT='角色菜单关联';
