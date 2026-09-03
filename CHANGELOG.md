# 更新日志

本文件遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 的组织方式，
版本号遵循 [SemVer](https://semver.org/lang/zh-CN/)。

每个版本固定分 **Breaking Changes / New Features / Bug Fixes** 三类
（见组织编码规范第 5 节）。没有内容的类别保留标题并写「无」，
这样使用者不必怀疑是遗漏还是确实没有。

## Unreleased

### Breaking Changes

- 无

### New Features

- 无

### Bug Fixes

- **空的「权限标识」不再打垮整个会话**。菜单管理新增/编辑菜单时「权限标识」留空，
  会被存成空串 `''`（不是 `NULL`）。该菜单授权给用户后，`TokenAuthenticationFilter`
  对每个带令牌的请求执行 `new SimpleGrantedAuthority("")`，触发
  `IllegalArgumentException`，导致该用户的所有已认证请求整体 500——`/api/auth/me`
  首当其冲，前端表现为登录后立即白屏。三层修复：
  - `TokenAuthenticationFilter.authoritiesOf` 静默跳过空白角色/权限项，一条脏数据
    不再决定整个会话可用性；
  - `SysRelationMapper.selectPermCodesByUserId` 的 SQL 加 `TRIM(m.perm_code) <> ''`，
    空串权限点不再进入用户权限集合，也不再下发给前端按钮显隐（`TRIM` 与 `<> ''`
    属 MySQL 5.7 安全子集）；
  - `SysMenuService.save` / `updateById` 覆写，落库前把空白 `permCode` / `icon`
    归一为 `NULL`，从写入口消除空串。
  既有 `''` 脏数据无需迁移，前两层已兜底。

## 0.2.1 (2026-09-02)

一批不改变兼容承诺的收敛：把 codegen 逐 Controller 内联的取参逻辑上移基类、
让 codegen 生成物用 Lombok 消除样板、放宽字典端点的权限要求。
`api/` 包只增不改，`0.2.0` 的接入方直接升级即可。

### Breaking Changes

- 无

### New Features

- **`framework-mybatis-starter` 的 `api/BaseController` 新增 6 个列表查询取参方法**：
  `text` / `asInt` / `asLong` / `asDecimal` / `asDate` / `asDateTime`（`protected static`，
  共用私有 `parseParam`）。语义与 codegen 此前内联的实现一致——空串按未填、解析失败抛
  `BizException(BAD_REQUEST)`、`asDateTime` 同时接受 `T` 与空格分隔。codegen 0.2.1
  起生成的 Controller 直接调用继承来的这几个方法，不再逐个内联。属 `api/` 兼容面，
  仅新增签名。
- **`archetype` 生成的工程预置 `org.projectlombok:lombok`（`optional`）依赖**。
  配合 codegen 0.2.1：生成的 Entity / Controller 改用 `@Getter` / `@Setter` /
  `@RequiredArgsConstructor`。框架自身源码不受影响（仍手写访问器 / 构造器 / Logger），
  `framework-bom` 不仲裁 lombok 版本（由 `spring-boot-dependencies` 仲裁）。
  详见 `CLAUDE.md` §4.10。
- **`framework-parent` 不再声明 lombok 依赖，也不再配 `annotationProcessorPaths`**。
  框架全仓 0 处使用 lombok；注解处理器绑 javac 内部非公开 API，与「任意 JDK ≥ 17
  都能直接构建框架本身」冲突。直接继承 `framework-parent` 且依赖它捎带 lombok 的
  业务工程（正常不应如此），需在自己的 `pom.xml` 显式声明。

### Bug Fixes

- **字典 `GET /system/dict/data/type/{dictType}` 端点降为仅需登录**，不再要求
  `system:dict:list` 权限点。字典项是跨页面共享的枚举数据，要求该权限会逼着每个用到
  字典下拉框的角色被授予「字典管理」，侧边栏因此多出不该有的管理入口。过滤链的
  `anyRequest().authenticated()` 已兜底，未登录仍拿不到。

## 0.2.0 (2026-08-31)

补齐一批"缺了不能上生产"的能力：服务端权限点校验、登录失败锁定、在线用户与强制下线，
并为插件化补上两个必要的挂载点（拦截器链扩展缝、可替换的缓存后端）。

此前权限点只下发给前端用于按钮显隐，
**服务端对所有非白名单接口一律只要求"已认证"**——任何能登录的账号
直接构造 HTTP 请求即可调用任何接口。界面看起来是受控的，实际并不受控。

### Breaking Changes

- **接口现在会真正校验权限点**。升级后，此前"能登录就能调"的接口将对
  未被授权的账号返回 403。`seed-rbac.sql` 已把全部权限点授予 `ADMIN`，
  用内置管理员登录不受影响；其他角色需要按 `sys_role_menu` 补授权。
  临时排查可用 `describeadmin.security.permission-enabled=false` 关闭，
  但那等于回到"界面受控、接口不受控"的状态，不应用于生产。
- `BaseController` 新增 `permPrefix()` 与 `requirePermission(String)` 两个
  `protected` 方法。子类若已有同名方法会与之冲突。
  构造函数签名未变，既有业务 Controller 无需改动。
- **`sys_dept` 新增 `ancestors` 列，`sys_role` 新增 `data_scope` 列，新增
  `sys_role_dept` 表**（数据权限）。`CREATE TABLE IF NOT EXISTS` 不会给已存在的表加列，
  升级前已建库的开发/测试环境需要重建。0.2.0 尚未发布到 Central、此时还没有真实业主库，
  是这条表结构变更成本最低的时间点。
- `AuthUser` / `LoginUser` 的构造函数新增 `deptId`、`dataScope`、`customDeptIds` 三个参数。
  保留了不含这三项的旧构造函数重载，业务方自定义的 `AuthUserLoader` 实现无需改动即可编译通过，
  只是新用户会退回 `DataScopeType.ALL`（不过滤）。
- **`ActiveSession` 构造函数新增 `ip`、`device` 两个参数**（`api/` 包）。0.2.0 尚未发布，
  不保留旧构造函数重载；业务方自定义的 `TokenStore` 实现若手工构造 `ActiveSession`，
  需要补两个参数（不关心时传 `null`）。
- **`GET /api/system/online` 的返回体由 `List<ActiveSession>` 变为
  `PageResult<ActiveSession>`**。前端需按分页信封（`records` / `total`）解析；
  本仓的 `@describeadmin/system-ui` 已同步。
- **`BaseController.permPrefix()` 从 `protected` 放宽为 `public`**——操作日志切面需要
  跨包读取它。纯粹的加法，不影响任何调用方；但任何业务方自己覆写过
  `permPrefix()` 的 Controller，若声明为 `protected`，会因为"子类不能降低父类方法可见性"
  这条 Java 规则编译失败，需要同步改成 `public`（本仓库的 `sample-app` 示例已同步）。
- 新增 `sys_dict_type`/`sys_dict_data`/`sys_config`/`sys_oper_log` 四张表（字典、
  参数配置、操作日志）。同样是对已建库环境的破坏性 schema 变更，与上一条数据权限的
  变更一起处理即可（同一次重建库覆盖两者）。
- **所有 `Long` / `long` 现在序列化为 JSON 字符串**（`describeadmin.web.json.long-as-string`，
  默认开启）。这是一条前端可见的破坏性变更：`id`、`createBy`、`updateBy`、`userId`、
  菜单 `id` 等字段的 TypeScript 类型由 `number` 变为 `string`。
  动机是 3.3 允许把主键切成雪花 ID，而 19 位的雪花 ID 超出 JS
  `Number.MAX_SAFE_INTEGER`，前端 `JSON.parse` 会静默舍入末几位——
  **列表显示正常，点编辑/删除却报「记录不存在」或改错行**。框架既然提供了这个开关，
  就得保证切过去之后整条链路是对的。
  `PageResult` 的 `total`/`current`/`size`/`pages` 用
  `@JsonFormat(shape = NUMBER)` 排除在外，仍是数字（`el-pagination` 的 `:total` 要求数字）。
  codegen 与三份前端 `auth.ts` 已同步；业务方自己的 TS 类型需要同步修改。
  确认不会用雪花 ID 的项目可以关掉这个开关，但切换后的故障是静默的。
- **时间类型的 JSON 输出格式由 ISO-8601 改为 `yyyy-MM-dd HH:mm:ss` 一族**
  （`LocalDate` 为 `yyyy-MM-dd`，`LocalTime` 为 `HH:mm:ss`，均可配置）。
  输入侧是**出严进宽**：`T` 与空格分隔都接受，秒与小数秒可省，
  因此已经在按 ISO 发数据的调用方不受影响。

### New Features

- `PermissionChecker`（`framework-common` 的 `api/` 包）—— 权限校验契约。
  与 `CurrentUserProvider` 同一范式：接口在 common，实现由
  framework-security-starter 注册，未引入时退回 `PERMIT_ALL`，
  使 framework-mybatis-starter 不必依赖 Spring Security。
- `SecurityContextPermissionChecker` —— 从 SecurityContext 读取权限点。
  权限快照来自登录时刻，与 `/api/auth/me` 下发给前端的是同一份。
- 启用 `@EnableMethodSecurity`，业务方可在自定义端点上用
  `@PreAuthorize("hasAuthority('模块:对象:动作')")`。
- `BaseController` 的五个通用端点自动校验权限点，前缀由 `@RequestMapping`
  路径推导（`/api/system/user` → `system:user`），可覆写。
- codegen 生成的 Controller 现在带 `permPrefix()` 显式覆写。
- 新增配置项 `describeadmin.security.permission-enabled`（默认 `true`）。
- 框架模块首次拥有单元测试；surefire 补上 `-Dfile.encoding=UTF-8`
  （sample-app 早已配置，框架侧因此前无测试而一直空缺）。

**framework-cache-starter** —— 新模块，缓存契约与零依赖内存实现

- `CacheProvider`（`api/` 包）—— `put` / `get` / `evict` / `increment` 四个方法。
  `increment` 单独给出而不是让调用方 get-then-put：后者在并发下丢计数，
  而它最典型的用途正是登录失败计数，丢计数等于爆破防护被绕过。
- `InMemoryCacheProvider` —— 无任何第三方依赖，与 `InMemoryTokenStore` 同一组取舍
  （重启丢失、不支持多实例）。带容量上限（`describeadmin.cache.max-size`，默认 10000），
  因为缓存键常含用户输入，无上限意味着任何人都能把堆撑爆。
- 集中式实现（Redis 等）以插件形态提供，本模块自身不允许引入任何缓存中间件依赖，
  由父 POM 的 enforcer 在构建期强制。

**登录失败锁定**

- 锁定窗口内连续失败达阈值后暂时拒绝该账号登录，使在线爆破从"受限于网络吞吐"
  变成"受限于锁定窗口"。到期自动解锁，不需要管理员介入。
- 对**不存在的账号同样计数**：只锁存在的账号会让"会不会被锁"成为账号枚举信道，
  使登录链路里用 DUMMY_HASH 抹平响应耗时的努力失效。
- 已知取舍：可被用于拒绝服务（知道用户名的人能故意锁住对方）。这是按用户名锁定的
  固有代价，应对是把窗口设短并允许整体关闭。
- 新增配置项 `describeadmin.security.lockout.{enabled,max-failures,duration}`
  （默认 `true` / `5` / `15m`）。

**在线用户与强制下线**

- `TokenStore.listActive()` —— **`default` 方法**，默认返回空列表。写成抽象方法会让
  所有已实现该接口的业务方直接编译失败；而确实存在无法枚举的实现（如把令牌委托给
  外部统一认证中心的实现），它们保留默认行为即可。
- `ActiveSession`（`api/` 包）—— 会话快照，**刻意不含令牌本身**：
  令牌一旦出现在这个响应里，任何能打开在线用户页的人都可以拿它冒充当事人。
  携带 `ip` / `device`（登录 IP 与设备描述），`TokenStore` 实现未记录来源时为 `null`。
- `SessionMeta`（`api/` 包）—— 一次登录的来源信息（IP + 设备），由 Web 层在登录时
  从 `HttpServletRequest` 提取。与之配套，`TokenStore` 新增 `issue(user, meta)` 与
  `issueWithRefresh(user, meta)` 两个 **`default` 重载**（默认忽略 `meta`，语义为
  "本实现不记录登录来源"）。刷新令牌时来源随会话延续。
- 设备描述由 `RequestClientInfo`（`framework-system-starter` 内部，非 `api/`）对
  `User-Agent` 做**轻量启发式**解析（`Chrome · Windows` 这种粒度），不引 UA 解析库。
  同一个工具也统一了"哪个头算客户端 IP"的判断（`X-Forwarded-For` 首段 → `X-Real-IP`
  → `remoteAddr`），`OperLogAspect` 一并改用它——操作日志此前不认 `X-Real-IP`。
- `GET /api/system/online` 返回 `PageResult<ActiveSession>`，接受 `PageQuery`
  （`current` / `size`）；`DELETE /api/system/online/{userId}` 不变。
  权限点 `system:online:list` / `system:online:remove`。分页在应用层对 `listActive()`
  的全量快照切片——`TokenStore` 没有分页入参，翻页会重复一次全量枚举，但这是低频管理页、
  在线会话数天然有界，换来的是与其余列表页一致的 `PageResult` 契约。
- 种子数据的在线用户菜单 `visible = 1`，前端「在线用户」页（列表 + 登录 IP/设备列
  + 分页 + 强制下线）已交付。

**第一个可选插件已独立成仓**

- `framework-cache-redis-starter`（把 `CacheProvider` 与 `TokenStore` 切到 Redis）
  **不在本仓库**，见 [describeadmin/framework-cache-redis-starter](https://github.com/describeadmin/framework-cache-redis-starter)。
  它有自己的版本线与 CHANGELOG。
- 它是"能力可插拔"的第一个真实证据：不引它行为与从前完全一致，
  引了它重启不掉线、多实例共享会话，而框架核心与业务代码一行都不用改。
- ⚠️ **`framework-bom` 刻意不仲裁插件版本**。插件版本与框架版本无对应关系，
  让 BOM 按 `${project.version}` 去解析插件，业务方会拿到一个根本不存在的制品，
  而报错只说"找不到"，不会有任何东西指向 BOM。引插件时请显式写版本号。

**插件版本兼容自检**

- `FrameworkVersion`（`framework-common` 的 `api/` 包）—— 插件声明自己要求的最低框架版本，
  在启动阶段校验，把"运行期某个请求里的 `NoSuchMethodError`"提前成"启动失败 + 一句可操作的信息"。
  插件以 `provided` 依赖框架，**运行时的框架版本由业务方决定**，不是插件构建时那个。
- 判定按风险区别对待：框架比要求的旧 → 启动失败；主版本不同 → 启动失败；
  框架更新且主版本相同 → 只记 WARN（一律拒绝会让每个框架小版本都逼所有插件重发一遍）；
  读不到版本 → 放行 + WARN（不能因为自检机制本身把应用挡在门外）。
- 版本号来自 `META-INF/describeadmin-framework.properties`（Maven 资源过滤写入），
  **不用 MANIFEST**：`Package.getImplementationVersion()` 只在从 jar 运行时有值，
  在 IDE 与 surefire 里都是 null，那会让自检在开发期永远失效——
  而开发期恰恰最该发现版本不匹配。

**插件规范落地**

- 新增 `docs/registry.md`：插件目录与准入规范。方案第八章此前只有一张登记表、
  没有任何准入条款，且该文件并不存在。
- 父 POM 的 enforcer 拆成两个 execution，`enforce-core-thin`（核心薄门禁）
  可被插件模块单独关闭，而 JDBC 驱动与 Jackson 3 两条对插件同样生效。
- `CLAUDE.md` 新增 4.6 节，含"跨模块 `@ConditionalOnMissingBean` 必须显式声明装配顺序"
  这条最隐蔽的坑。

**MyBatis 拦截器链扩展缝**

- `MybatisPlusInterceptor` 改为收集容器里所有 `InnerInterceptor` Bean（按 `@Order` 升序），
  统一排在框架自带的分页与乐观锁之前。此前插件想加拦截器只能整体覆盖这个 Bean，
  代价是把分页配置逻辑复制一份，之后框架改了这里复制出去的那份不会跟着改。
- 顺序不是随意定的：多租户、动态表名、数据权限这类**改写 SQL** 的拦截器必须先于分页，
  否则分页的 count 语句基于未改写的 SQL 生成，会统计出被改写条件排除掉的行。

**数据权限**（阶段 D，若依同款的五档模型：全部 / 自定义部门 / 本部门 / 本部门及以下 / 仅本人）

- 直接复用 MyBatis-Plus 自带的 `DataPermissionInterceptor`（已随 `mybatis-plus-jsqlparser`
  引入，不新增依赖），按表名注入 WHERE 条件，覆盖 SELECT/UPDATE/DELETE——连
  `BaseController.get()/update()/delete()` 这类走 `selectById`/`updateById` 的路径
  也管得到，不止是分页列表。上面这条 `InnerInterceptor` 扩展缝新增后第一次派上用场。
- 范围挂在角色（`sys_role.data_scope`）上，不挂在部门/岗位——框架目前没有"岗位"概念。
  一个用户身兼多角色时取全部角色里最宽松的一档生效（全部 > 本部门及以下 > 自定义部门 >
  本部门 > 仅本人），这是刻意选的简单规则；多角色按 OR 取并集留作后续增强。
- 表要不要参与过滤是显式登记（`DataScopeTableCustomizer`，与收集自定义拦截器同一手法），
  不做注解 + 反射扫描。`framework-system-starter` 只登记了 `sys_user`——角色/菜单/部门本身
  是权限体系的配置数据，不应被数据权限过滤掉。
- `sys_dept` 新增 `ancestors` 物化路径列（逗号分隔的祖先部门 id，不含自身），
  `FIND_IN_SET` 判断"是否是下级部门"，5.7-safe，不依赖 CTE。移动部门时
  `SysDeptService` 会级联更新全部子孙的 `ancestors`，并拒绝把部门移动到自己的子部门下
  （防止路径成环）。
- 新增配置项 `describeadmin.mybatis.data-scope.enabled`（默认 `true`）。
- `GET`/`PUT /api/system/role/{roleId}/depts`：自定义数据权限的部门列表，
  权限点 `system:role:assign-dept`，与已有的 `.../menus` 同一"重建"语义。
- ADMIN 角色的 `data_scope` 由种子数据显式置为"全部"，代码里不特判 `role_code == 'ADMIN'`，
  与 ADMIN 靠种子数据被授予全部菜单是同一手法。

**字典 + 参数配置**（阶段 E）

- `sys_dict_type`/`sys_dict_data`：字典类型与数据两张表、两个 Controller，共用权限前缀
  `system:dict`（同一个管理页面的两个面板，见两者都覆写的 `permPrefix()`）。
  `GET /api/system/dict/data/type/{dictType}` 取某类型下全部启用中的字典项，供前端下拉框用。
- `sys_config`：系统参数配置，`SysConfigService.getValue(key[, default])` 是给框架其余
  模块直接调用的便捷方法，不必都经过 HTTP。
- 两者的查询都读穿 `CacheProvider`（新增配置项 `describeadmin.system.dict.cache-ttl` /
  `describeadmin.system.config.cache-ttl`，默认均为 30 分钟），写操作后主动失效对应缓存
  ——`framework-cache-starter` 的类注释早先就点名"字典快照"是它的目标场景之一。
  `framework-system-starter` 因此新增对 `framework-cache-starter` 的依赖，
  与 `framework-security-starter` 引入它的理由相同。

**操作日志**（阶段 E）

- 新增 `framework-system-starter` 对 `spring-boot-starter-aop` 的依赖（轻量级，
  不在 `enforce-core-thin` 封禁清单里）。两条切入路径，与权限校验"框架托底通用路径 +
  自定义路径显式声明"是同一个心智模型：
  - `BaseController` 的 create/update/delete 三个通用端点自动记录，业务方无需任何标注——
    覆写了这三个方法的子类（如 `SysDeptController`）同样被记录，AspectJ 的
    `execution(Type+.method(..))` 按整个类型层级匹配方法签名，覆写不会让它从
    "这是一次 BaseController.create() 执行"里消失。
  - 自定义端点用新注解 `@OperLog(module, description)` 显式声明，已给
    `SysUserController`/`SysRoleController`/`SysOnlineController` 的自定义写端点补上。
- **请求参数里但凡 key 命中 `password`/`pwd`/`secret`/`token`（大小写不敏感、子串匹配）
  的字段，整段替换为 `***` 再落库**——这是唯一的安全底线，不做的话
  `POST /api/system/user/with-password` 的明文密码会原样进 `sys_oper_log`。
- 失败的操作同样落日志（`status` 标记失败 + 记录异常信息），不会被静默吞掉；
  日志本身写入失败只记 `slf4j` 错误，绝不向上抛出拖累真正的业务操作。
- 同步写入，不做异步——中小规模项目下单行 INSERT 的延迟可以接受，真到了需要异步的量级
  再加，不预先绕路。
- `sys_oper_log` **不继承 `BaseEntity`**：只追加、不给用户改的审计表，审计字段/逻辑删除/
  乐观锁语义不适用，与 `SysOnlineController` 不继承 `BaseController` 是同一类判断。
- `GET /api/system/oper-log`（分页 + 按 module/operatorName/status/时间范围筛选）、
  `DELETE /api/system/oper-log/{id}`、`DELETE /api/system/oper-log/clean`（清空，
  复用删除的权限点，不单独开一个权限对象）。
- 新增配置项 `describeadmin.system.oper-log.enabled`（默认 `true`）。
- `FrameworkJsonModule`（framework-web-starter）—— 框架的 JSON 序列化约定，
  开关前缀 `describeadmin.web.json.*`。注册为 `Module` Bean 而不是自定义
  `ObjectMapper`：后者会顶掉 Spring Boot 的全部默认配置并让业务方的
  `spring.jackson.*` 失效。`enabled=false` 可整体退回 Boot 默认行为。
  `BigDecimal` 刻意不转字符串（理由见 CLAUDE.md 4.8）。
- 可注入的 `Clock` Bean（framework-mybatis-starter，`@ConditionalOnMissingBean`）。
  `AuditMetaObjectHandler` 的审计时间改从它读取，业务方在测试里注入
  `Clock.fixed(...)` 即可断言具体时刻，不必再靠 sleep 或容差比较验证
  「创建后 N 天过期」这类逻辑。原有两个构造函数保留为重载，调用方无需改动。
- `TreeBuilder` 上提到 `framework-common` 的 `api/` 包
  （原 `io.github.describeadmin.system.core.TreeBuilder`）。建树是业务方也会用到的
  通用能力，留在系统管理模块里拿不到。原类保留为 `@Deprecated(forRemoval = true)`
  的转发实现，将在 0.3.0 移除。
- framework-web-starter 建立测试底座（此前没有任何测试）：24 个用例，
  含一组走真实 Spring MVC 链路的 MockMvc 往返测试——只断言 Module Bean 存在
  是不够的，Bean 建了却没被 `MappingJackson2HttpMessageConverter` 装上
  是一种启动毫无异常的静默失败。
- **内嵌 Servlet 容器由 Tomcat 换成 Undertow**。`framework-web-starter` 排掉
  `spring-boot-starter-tomcat`、改引 `spring-boot-starter-undertow`，业务方引框架
  即得到 Undertow，无需在自己的工程里做任何依赖调整。仅有的使用者可见影响是
  容器配置项的前缀：`server.tomcat.*` 不再生效，需改用 `server.undertow.*`
  （`server.port` / `server.servlet.*` 等通用项不变）。0.2.0 尚未发布，是切换成本最低的时间点。

### Bug Fixes

- `AccessDeniedException` 此前会被 framework-web-starter 的
  `@ExceptionHandler(Throwable.class)` 兜底吞掉，映射成 **HTTP 500 + code 50000**，
  而不是 403。新增 `SecurityExceptionHandler`（`@Order(HIGHEST_PRECEDENCE)`）
  显式接住，产出与 `ResultAuthenticationEntryPoint` 逐字一致的响应体。
  这个缺陷此前不可见——没有任何代码会抛 `AccessDeniedException`。
- codegen 生成的模块若模块名含下划线，权限点会静默错配：
  `apiPrefix` 默认把 `my_module` 转成 `/api/my-module`（推导得 `my-module:list`），
  而 `menu-*.sql` 登记的是 `my_module:list`，表现为连 ADMIN 都被 403。
  现由生成的 `permPrefix()` 覆写消除。
- **带 `datetime` 字段的模块，新增/编辑表单提交一直是坏的**。codegen 生成的
  日期时间选择器发 `yyyy-MM-dd HH:mm:ss`（空格分隔），而 Spring Boot 默认按
  ISO-8601 反序列化 `LocalDateTime`，解析必然失败。
  查询参数那条路 codegen 自己手工绕过了（`value.replace(' ', 'T')`），
  `@RequestBody` 这条路没有，于是"搜索能用、新增不能用"。
  此前从未被发现，是因为 `examples/project.yaml` 只用了 `date`、
  `sample-app` 也没有任何 `datetime` 字段——全仓没有一处触发过这条路径。
  现由 `FrameworkJsonModule` 的宽松解析器修复。
  注意实测该故障返回的是 **500 而不是 400**，原因见下条。

> **已知缺陷（本版本未处置）**：`GlobalExceptionHandler` 没有处理
> `HttpMessageNotReadableException`，因此任何请求体解析失败（日期格式错、
> JSON 语法错、类型对不上）都会落到 `@ExceptionHandler(Throwable.class)`
> 兜底返回 500。"日期填错了"是使用者的输入问题，报服务器内部错误会把排查方向带偏。
> 详见 `docs/VERSION_BASELINE.md` 发现 ⑳。

---

## 0.1.1 (2026-08-20)

本版本只为交付业务方脚手架。**框架六个模块无任何功能变更**，
随 archetype 一起走版本线，是为了让"archetype 的版本 == 它生成的工程引用的框架版本"
这条约定成立——archetype 的版本号在构建期由 `${project.version}` 写死进生成物。

### Breaking Changes

无。

### New Features

**describeadmin-archetype** — 业务方工程脚手架（方案 9.2.2）

- `mvn archetype:generate` 一条命令生成**没有业务模块、但可直接登录**的工程，
  不再需要"以 `sample-app` 为起点复制后删掉示例模块"
- 生成物 6 个文件，不含任何 SQL：RBAC 的建表与种子脚本在
  `framework-system-starter` 的 jar 里，通过 `classpath:` 引用
- 把两个已实测的接入坑固化为默认正确状态：`mysql.version=8.2.0` 覆盖、
  `spring.sql.init.encoding=UTF-8`
- **刻意不生成 `maven-toolchains-plugin` 配置**：业务方只需 Maven 跑在 JDK 17+，
  用哪个 JDK 构建由业务方自己决定；产物由 `release=17` 钉死
- CI 新增 `archetype-e2e`：生成 → 用 JDK 17 构建 → 起真实 MySQL 5.7 → 登录 →
  按字节核验中文

### Bug Fixes

无。

---

## 0.1.0 (2026-08-20)

首个公开版本。

### Breaking Changes

无——首个版本，无历史包袱。

自本版本起，`api/` 包下的 public 签名进入兼容性承诺范围，
后续任何变更都按 SemVer 处理。

### New Features

**framework-bom** — 统一版本仲裁

- 业务方 `import` 这一个 BOM 即可，全部框架模块不写版本号
- 提供 `mysql-connector-j` 的 5.7-safe 默认值 `8.2.0`
  （8.3.0 起官方不再支持 MySQL 5.7，此默认值是有意钉住的）
- 框架自身不引入任何 JDBC 驱动，驱动由业务方声明

**framework-common** — 通用契约

- `Result<T>` 统一响应结构，Controller 一律返回它
- 全局异常处理器，业务代码不需要 try-catch 后拼错误码

**framework-web-starter** — Web 层能力

- `TraceIdFilter`：为每个请求生成 traceId 并放入 MDC，可打进日志与响应头
- 配置前缀 `describeadmin.web.trace.enabled`

**framework-security-starter** — 认证与鉴权

- 不透明令牌（opaque token）签发与校验链路
- `TokenStore` SPI 与默认的 `InMemoryTokenStore` 实现
- `AuthProvider` SPI：新增登录方式只需实现接口并注册为 Bean，
  框架通过 `List<AuthProvider>` 自动收集。框架核心代码中不出现任何
  具体实现的名字
- 按钮级权限点校验
- `FrameworkSystemAutoConfiguration` 显式声明自身的 `@MapperScan` 与 `@ComponentScan`：
  业务方一旦写了 `@MapperScan("自己的包")`，MyBatis 的自动扫描就不再生效，
  框架的系统管理 Mapper 会全部扫不到、登录直接失败。框架自行登记扫描路径
  把这个坑消化在框架侧，业务方照常写自己的 `@MapperScan` 即可

**framework-mybatis-starter** — 持久层基类

- `BaseEntity` 统一承担审计字段（创建人/创建时间/更新人/更新时间/逻辑删除），
  业务实体不再重复定义
- `BaseService` / `BaseController` 承载通用 CRUD，业务代码保持「薄」
- `BaseController.buildListWrapper` 覆写点，供业务定制列表查询条件
- 分页 `DbType` 是配置项而非硬编码，便于切换到达梦/金仓/OceanBase
- 主键策略走全局配置（`IdType.AUTO` 默认），实体类不硬编码 `@TableId`

**framework-system-starter** — 开箱即用的 RBAC

- 用户 / 角色 / 菜单 / 部门四套管理能力
- 完整菜单树与按钮级权限点
- 业务方引入依赖即得到可登录、带权限体系的后台，
  框架修了 bug 升个版本就能拿到——不需要把这些代码复制进业务仓库

### Bug Fixes

无——首个版本。

### 已知限制

诚实记录，避免使用者踩空：

- **业务工程模板（archetype）尚未交付。** 手工照抄 POM 会踩两个已实测的坑：
  BOM 中的驱动版本被父 POM 的 `dependencyManagement` 覆盖（连 MySQL 5.7 直接失败），
  以及多 JDK 共存导致 `release=17` 编译失败。两者的现象与真实原因相距很远。
  **在 archetype 交付之前，请以 `sample-app` 仓库为起点，不要从空工程手写 POM。**
- 支持矩阵目前实测过 MySQL 5.7；国产化库（达梦/金仓/OceanBase）的兼容性
  按 5.7 安全子集设计，但尚未逐一实测。
