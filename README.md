# describeadmin framework

describeadmin 的后端框架核心。发布到 Maven Central，groupId `io.github.describeadmin`。

**从零开始用，请看 [快速开始](https://github.com/describeadmin/docs/blob/main/QUICKSTART.md)。**
本文只讲这个仓库本身。

## 引入

只 `import` 一个 BOM，其余模块**一律不写版本号**：

```xml
<properties>
  <describeadmin.version>0.1.1</describeadmin.version>
  <!-- ⚠️ 必须显式声明，见下文「驱动版本」 -->
  <mysql.version>8.2.0</mysql.version>
</properties>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.describeadmin</groupId>
      <artifactId>framework-bom</artifactId>
      <version>${describeadmin.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

> 别手写这份 POM，用脚手架生成——它已经把两个已实测的接入坑固化成默认正确的状态：
>
> ```bash
> mvn archetype:generate -B \
>   -DarchetypeGroupId=io.github.describeadmin \
>   -DarchetypeArtifactId=describeadmin-archetype \
>   -DarchetypeVersion=0.1.1 \
>   -DgroupId=com.acme -DartifactId=my-server -Dpackage=com.acme.myserver
> ```
>
> 生成的是**没有业务模块、但可直接登录**的工程，没有任何东西需要事后删除。
> 模板本身见 [`describeadmin-archetype/`](describeadmin-archetype/)。

## 模块

| 模块 | 给你什么 |
|---|---|
| `framework-bom` | 统一版本仲裁 |
| `framework-common` | `Result<T>` 统一响应结构、全局异常处理器、`PermissionChecker` / `FrameworkVersion` 契约 |
| `framework-web-starter` | traceId 贯穿请求与日志 |
| `framework-security-starter` | 不透明令牌认证、`AuthProvider` / `TokenStore` SPI、服务端权限点校验、登录失败锁定、在线会话枚举 |
| `framework-cache-starter` | `CacheProvider` 缓存契约与零依赖内存实现 |
| `framework-mybatis-starter` | `BaseEntity` / `BaseService` / `BaseController` 基类，审计字段、逻辑删除、拦截器链扩展缝、数据权限拦截器 |
| `framework-system-starter` | 开箱可用的用户 / 角色 / 菜单 / 部门 / 在线用户管理 + 数据权限 + 字典 / 参数配置 / 操作日志（含建表与种子 SQL） |
| `describeadmin-archetype` | 业务方工程脚手架，与框架同版本发布 |

## 已完成的后端能力

> Maven Central 当前可发布版本是 **0.1.1**；下表含已在本仓库开发分支完成、
> 尚待合并与发布的 **0.2.0** 能力（合并与发布进度见
> [`docs/PROGRESS.md`](https://github.com/describeadmin/docs/blob/main/PROGRESS.md)）。
> 逐条变更见 [CHANGELOG.md](./CHANGELOG.md)。

**认证与权限**（`framework-security-starter` + `framework-system-starter`）

- `POST /api/auth/login`、`POST /api/auth/logout`、`GET /api/auth/me`、
  `GET /api/auth/menus`（当前用户菜单树）、`GET /api/auth/providers`（已启用的登录方式）
- 不透明令牌（非 JWT）：默认内存实现，可整体换成 Redis 等集中式实现而上层代码不变；
  支持"禁用账号立即失效""强制下线"
- **服务端权限点校验**：`BaseController` 的五个通用端点与自定义端点均按
  `<模块>:<对象>:<动作>` 校验，不再只是前端按钮显隐
- 登录失败锁定：连续失败达阈值后按用户名暂时锁定，对不存在的账号同样计数以避免账号枚举

**系统管理**（`framework-system-starter`，均为 `Result<T>` 响应 + 逻辑删除）

- 用户：列表/详情/改密（`PUT .../{userId}/password`）、角色分配（`GET`/`PUT .../{userId}/roles`）；
  建号需走 `POST .../with-password`，通用建号端点已显式禁用以避免明文密码落库
- 角色：标准 CRUD + 菜单授权（`GET`/`PUT .../{roleId}/menus`）
- 菜单：标准 CRUD + 全量树（`GET .../tree`）
- 部门：标准 CRUD + 树形查询（`GET .../tree`）
- 在线用户：会话列表与强制下线（`GET`/`DELETE /api/system/online`），直接读
  `TokenStore`，不落表，因此没有对应数据库表

**数据权限**（`framework-mybatis-starter` + `framework-system-starter`，若依同款的五档模型）

- 全部 / 自定义部门 / 本部门 / 本部门及以下 / 仅本人，挂在角色（`sys_role.data_scope`）上；
  一个用户身兼多角色时取全部角色里最宽松的一档生效
- 复用 MyBatis-Plus 自带的 `DataPermissionInterceptor` 按表名注入 WHERE 条件，覆盖
  SELECT/UPDATE/DELETE——连 `BaseController.get()/update()/delete()` 这类走
  `selectById`/`updateById` 的路径也管得到，不止是分页列表
- 表要不要参与过滤是显式登记（`DataScopeTableCustomizer`），默认不影响业务方自己的表
- `sys_dept` 新增 `ancestors` 物化路径列支撑"本部门及以下"，移动部门时自动级联更新
  全部子孙的路径，并拒绝把部门移动到自己的子部门下
- `GET`/`PUT /api/system/role/{roleId}/depts`：自定义数据权限的部门列表
- 新增配置项 `describeadmin.mybatis.data-scope.enabled`（默认 `true`）

**字典 + 参数配置**（`framework-system-starter`，读穿 `CacheProvider`）

- 字典类型 + 字典数据两张表，共用权限前缀 `system:dict`（同一个管理页面的两个面板）；
  `GET /api/system/dict/data/type/{dictType}` 供前端下拉框按类型取值
- 参数配置 `sys_config`：`SysConfigService.getValue(key[, default])` 给框架其余模块
  直接调用，不必都经过 HTTP
- 两者的查询默认缓存 30 分钟（`describeadmin.system.dict.cache-ttl` /
  `describeadmin.system.config.cache-ttl` 可调），写操作后主动失效对应缓存

**操作日志**（`framework-system-starter`，`sys_oper_log`）

- 两条捕获路径：`BaseController` 的 create/update/delete 自动记录，不需要子类做任何事；
  自定义端点用 `@OperLog(module, description)` 显式声明——与权限校验"框架托底通用路径 +
  自定义路径显式声明"是同一个心智模型
- **请求参数里 key 命中 `password`/`pwd`/`secret`/`token` 的字段会被整段替换为 `***`
  再落库**，不分大小写
- 失败的操作同样落日志（记录失败状态与异常信息），日志写入本身失败只记错误日志、
  不影响真正的业务操作
- `GET /api/system/oper-log`（分页 + 按模块/操作人/状态/时间范围筛选）、
  `DELETE /api/system/oper-log/{id}`、`DELETE /api/system/oper-log/clean`（清空）
- 新增配置项 `describeadmin.system.oper-log.enabled`（默认 `true`）

**缓存契约**（新模块 `framework-cache-starter`）

- `CacheProvider`：`put` / `get` / `evict` / `increment` 四个方法，默认零依赖内存实现（带容量上限）
- 集中式实现以插件形式提供——第一个插件 `framework-cache-redis-starter` 已独立成仓（未发布到 npm/Central）

**持久层扩展缝**（`framework-mybatis-starter`）

- `MybatisPlusInterceptor` 收集容器内全部 `InnerInterceptor` Bean 并按 `@Order` 排序，
  插件可挂多租户 / 数据权限等改写 SQL 的拦截器，无需整体覆盖分页配置

**插件版本自检**（`framework-common`）

- `FrameworkVersion.requireCompatible()`：插件启动期校验所需的最低框架版本，
  把运行期才会暴露的 `NoSuchMethodError` 提前成启动失败 + 可操作的错误信息

## 两条容易被"顺手升级"掉的约定

这两个版本是**有意钉住**的，不是过时待升级：

- **Spring Boot 3.5.16，不是 4.x**
- **`mysql-connector-j` 8.2.0** —— Connector/J 自 8.3.0 起官方不再支持
  MySQL 5.7（"8.0 and later"）。8.2.0 是最后一个支持 5.7 的版本

### 驱动版本

框架的任何模块都**不引入 JDBC 驱动**，驱动由你自己声明。
`framework-bom` 只给出 5.7-safe 的默认值。

但如果你的父 POM 是 `spring-boot-starter-parent`，
**它继承来的 `dependencyManagement` 优先级高于你 import 的 BOM**，
BOM 里的默认值不会生效。必须在自己的 `<properties>` 里显式写
`<mysql.version>8.2.0</mysql.version>`。

漏了这一条的现象是「连 MySQL 5.7 直接失败」，看不出和版本有关。

## SPI 扩展

新增登录方式：实现 `AuthProvider` 并注册为 Bean 即可，
框架通过 `List<AuthProvider>` 自动收集。

```java
public interface AuthProvider {
    String type();
    boolean supports(String type);
    LoginUser authenticate(AuthRequest request);
}
```

框架核心代码里不出现任何具体实现的名字——具体渠道的名字只能出现在对应的
ext 模块内。

## 兼容性承诺

`api/` 包下的 public 签名 = 兼容性承诺范围，变更走 SemVer。
`core/` 与 `util/` 不在承诺范围内。

## 自己构建

```bash
mvn clean install                     # 需要 JDK 21（通过 toolchains 指定）
mvn clean verify -Prelease -Dgpg.skip=true   # 验证发布产物齐备
```

⚠️ **不要用 `java -version` 判断构建 JDK**——本项目通过 Maven Toolchains
选择 JDK，`PATH` 上是什么与构建用什么无关。首次配置见
[toolchains.xml.sample](https://github.com/describeadmin/docs/blob/main/scripts/toolchains.xml.sample)。

## 变更记录

见 [CHANGELOG.md](./CHANGELOG.md)。

## 许可证

[Apache-2.0](./LICENSE)
