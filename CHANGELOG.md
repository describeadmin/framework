# 更新日志

本文件遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 的组织方式，
版本号遵循 [SemVer](https://semver.org/lang/zh-CN/)。

每个版本固定分 **Breaking Changes / New Features / Bug Fixes** 三类
（见组织编码规范第 5 节）。没有内容的类别保留标题并写「无」，
这样使用者不必怀疑是遗漏还是确实没有。

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
