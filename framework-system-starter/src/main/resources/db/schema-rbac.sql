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
  -- 手机号/邮箱：核心的常规联系方式字段，可空。是否存在对应的登录方式（短信/邮箱验证码）
  -- 取决于有没有装配对应的 AuthProvider 插件，字段本身跟登录方式无关，参见 CLAUDE.md 4.6 的例外说明
  mobile       VARCHAR(20)      NULL                COMMENT '手机号，可空，非空时应用层保证唯一',
  email        VARCHAR(128)     NULL                COMMENT '邮箱，可空，非空时应用层保证唯一',
  dept_id      BIGINT           NULL                COMMENT '所属部门ID',
  status       TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
  -- 下次登录必须改密：管理员建号 / 重置密码后置 1，用户自助改密成功后清 0。
  -- 密码「定期过期」不写这一列，而是登录时按 pwd_update_time + sys.password.max-age-days 算出来。
  pwd_reset_required TINYINT  NOT NULL DEFAULT 0    COMMENT '下次登录必须改密：1是 0否',
  -- 密码最后修改时间，供「定期强制过期」判断。旧库升级后为 NULL，按「未过期」处理。
  pwd_update_time    DATETIME     NULL              COMMENT '密码最后修改时间',
  create_by    BIGINT           NULL                COMMENT '创建人',
  create_time  DATETIME         NULL                COMMENT '创建时间',
  update_by    BIGINT           NULL                COMMENT '更新人',
  update_time  DATETIME         NULL                COMMENT '更新时间',
  deleted      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删 1已删',
  version      INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (id),
  -- 注意：逻辑删除下不能对 username/mobile/email 建唯一索引，否则删除后无法复用同名账号/手机号/邮箱。
  -- 唯一性由应用层在「未删除」范围内校验。
  KEY idx_sys_user_username (username),
  KEY idx_sys_user_mobile (mobile),
  KEY idx_sys_user_email (email)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci
  COMMENT='用户';

-- 历史密码。只追加、由 SysUserService 在每次设密码时写入，不继承 BaseEntity 的审计/逻辑删除/乐观锁
-- （同 sys_oper_log）。仅当 sys.password.history-count > 0 时参与校验：新密码不得命中最近 N 条。
CREATE TABLE IF NOT EXISTS sys_user_password_history (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id       BIGINT       NOT NULL                COMMENT '用户ID',
  password_hash VARCHAR(100) NOT NULL                COMMENT '历史密码（BCrypt 哈希）',
  create_time   DATETIME         NULL                COMMENT '产生时间',
  PRIMARY KEY (id),
  KEY idx_sys_user_pwd_hist_user (user_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci
  COMMENT='用户历史密码';

CREATE TABLE IF NOT EXISTS sys_role (
  id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_code    VARCHAR(64) NOT NULL                COMMENT '角色标识',
  role_name    VARCHAR(64) NOT NULL                COMMENT '角色名称',
  sort         INT         NOT NULL DEFAULT 0      COMMENT '排序',
  -- 1全部 2自定义部门 3本部门 4本部门及以下 5仅本人，与 DataScopeType.getCode() 对应
  data_scope   TINYINT     NOT NULL DEFAULT 3      COMMENT '数据权限范围',
  -- 取值须为 sys_menu.path 中真实存在的路径；为空表示该角色不覆盖，使用前端全局 defaultHomePath
  home_path    VARCHAR(191)    NULL                COMMENT '默认首页路径',
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

-- 角色自定义数据权限的部门列表，只在对应角色 data_scope = 2（自定义部门）时有意义
CREATE TABLE IF NOT EXISTS sys_role_dept (
  role_id BIGINT NOT NULL COMMENT '角色ID',
  dept_id BIGINT NOT NULL COMMENT '部门ID',
  PRIMARY KEY (role_id, dept_id),
  KEY idx_sys_role_dept_dept (dept_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci
  COMMENT='角色自定义数据权限部门关联';

CREATE TABLE IF NOT EXISTS sys_dept (
  id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  parent_id    BIGINT      NOT NULL DEFAULT 0      COMMENT '父部门ID，0为根',
  dept_name    VARCHAR(64) NOT NULL                COMMENT '部门名称',
  leader       VARCHAR(64)     NULL                COMMENT '负责人',
  phone        VARCHAR(32)     NULL                COMMENT '联系电话',
  sort         INT         NOT NULL DEFAULT 0      COMMENT '排序',
  status       TINYINT     NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
  -- 从根到直接父级的祖先部门 id，逗号分隔，不含自身；顶级部门（parent_id=0）为空串。
  -- 由 SysDeptService 维护，供"本部门及以下"用 FIND_IN_SET 判断下级关系（5.7-safe）。
  ancestors    VARCHAR(500) NOT NULL DEFAULT ''     COMMENT '祖先部门id，逗号分隔',
  create_by    BIGINT          NULL                COMMENT '创建人',
  create_time  DATETIME        NULL                COMMENT '创建时间',
  update_by    BIGINT          NULL                COMMENT '更新人',
  update_time  DATETIME        NULL                COMMENT '更新时间',
  deleted      TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  version      INT         NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (id),
  KEY idx_sys_dept_parent (parent_id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci
  COMMENT='部门';

CREATE TABLE IF NOT EXISTS sys_dict_type (
  id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  dict_type    VARCHAR(64) NOT NULL                COMMENT '字典类型编码',
  dict_name    VARCHAR(64) NOT NULL                COMMENT '字典名称',
  status       TINYINT     NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
  create_by    BIGINT          NULL                COMMENT '创建人',
  create_time  DATETIME        NULL                COMMENT '创建时间',
  update_by    BIGINT          NULL                COMMENT '更新人',
  update_time  DATETIME        NULL                COMMENT '更新时间',
  deleted      TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  version      INT         NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (id),
  -- 逻辑删除下不建唯一索引，理由同 sys_user.username
  KEY idx_sys_dict_type_type (dict_type)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci
  COMMENT='字典类型';

CREATE TABLE IF NOT EXISTS sys_dict_data (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  dict_type    VARCHAR(64)  NOT NULL                COMMENT '所属字典类型编码',
  dict_label   VARCHAR(64)  NOT NULL                COMMENT '字典标签，前端展示用',
  dict_value   VARCHAR(191) NOT NULL                COMMENT '字典值，业务存储用',
  sort         INT          NOT NULL DEFAULT 0      COMMENT '排序',
  status       TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
  create_by    BIGINT           NULL                COMMENT '创建人',
  create_time  DATETIME         NULL                COMMENT '创建时间',
  update_by    BIGINT           NULL                COMMENT '更新人',
  update_time  DATETIME         NULL                COMMENT '更新时间',
  deleted      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  version      INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (id),
  KEY idx_sys_dict_data_type (dict_type)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci
  COMMENT='字典数据';

CREATE TABLE IF NOT EXISTS sys_config (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  config_key   VARCHAR(128) NOT NULL                COMMENT '参数键',
  config_value VARCHAR(500) NOT NULL                COMMENT '参数值',
  config_name  VARCHAR(64)      NULL                COMMENT '参数名称',
  -- Y/N，是否内置；只由种子数据写入，Service 层拒绝删除内置记录
  config_type  VARCHAR(1)       NULL                COMMENT '是否内置',
  create_by    BIGINT           NULL                COMMENT '创建人',
  create_time  DATETIME         NULL                COMMENT '创建时间',
  update_by    BIGINT           NULL                COMMENT '更新人',
  update_time  DATETIME         NULL                COMMENT '更新时间',
  deleted      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  version      INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
  PRIMARY KEY (id),
  KEY idx_sys_config_key (config_key)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci
  COMMENT='系统参数配置';

-- 只追加、由 OperLogAspect 写入，不继承 BaseEntity 的审计字段/逻辑删除/乐观锁语义
-- （见 SysOperLog 的类注释），所以没有 create_by/update_by/update_time/deleted/version。
CREATE TABLE IF NOT EXISTS sys_oper_log (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  module         VARCHAR(64)      NULL                COMMENT '模块，如 system:dept',
  description    VARCHAR(128)     NULL                COMMENT '操作描述，如"创建部门"',
  http_method    VARCHAR(16)      NULL                COMMENT 'HTTP 方法',
  request_url    VARCHAR(255)     NULL                COMMENT '请求路径',
  operator_id    BIGINT           NULL                COMMENT '操作人ID',
  operator_name  VARCHAR(64)      NULL                COMMENT '操作人用户名',
  operator_ip    VARCHAR(64)      NULL                COMMENT '操作人IP',
  -- 脱敏后的请求参数 JSON，截断到 2000 字符，见 OperLogAspect
  request_param  VARCHAR(2000)    NULL                COMMENT '请求参数（已脱敏）',
  status         TINYINT      NOT NULL DEFAULT 1      COMMENT '1成功 0失败',
  error_msg      VARCHAR(2000)    NULL                COMMENT '失败时的异常信息',
  cost_time      BIGINT           NULL                COMMENT '耗时，毫秒',
  create_time    DATETIME         NULL                COMMENT '操作时间',
  PRIMARY KEY (id),
  KEY idx_sys_oper_log_operator (operator_id),
  KEY idx_sys_oper_log_create_time (create_time)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci
  COMMENT='操作日志';
