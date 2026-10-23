# describeadmin framework

describeadmin 的后端框架：开箱可用的 RBAC、数据权限、审计与操作日志、登录防护、系统管理，
以 Spring Boot starter 的形式发布到 Maven Central（groupId `io.github.describeadmin`）。

**文档：<https://describeadmin.github.io/docs/>**，从 [从零跑起一个管理后台](https://describeadmin.github.io/docs/guide/getting-started) 开始。

## 开始使用

用脚手架生成工程，不要手写 POM（脚手架已经避开了驱动版本、迁移模式这几个报错与原因相距很远的坑）：

```bash
mvn archetype:generate -B \
  -DarchetypeGroupId=io.github.describeadmin \
  -DarchetypeArtifactId=describeadmin-archetype \
  -DarchetypeVersion=1.0.0 \
  -DgroupId=com.acme -DartifactId=my-server -Dpackage=com.acme.myserver
```

生成的工程 import 一个 BOM，其余模块不写版本号：

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.describeadmin</groupId>
      <artifactId>framework-bom</artifactId>
      <version>1.0.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

## 模块

| 模块 | 内容 |
|---|---|
| `framework-bom` | 统一版本仲裁 |
| `framework-common` | `Result<T>`、全局异常处理、`PermissionChecker` 等契约 |
| `framework-web-starter` | traceId、JSON 序列化约定 |
| `framework-security-starter` | 令牌认证、`AuthProvider` / `TokenStore` SPI、权限点校验、渐进式验证码、登录锁定 |
| `framework-mybatis-starter` | `BaseEntity` / `BaseService` / `BaseController`、审计字段、数据权限 |
| `framework-cache-starter` | `CacheProvider`、`LockOperations` 契约与内存实现 |
| `framework-storage-starter` | `StorageProvider` 契约与本地磁盘实现 |
| `framework-notify-starter` | `NotifyChannel` 契约与日志渠道 |
| `framework-system-starter` | 用户 / 角色 / 菜单 / 部门 / 字典 / 参数 / 操作日志 / 在线用户 / 站内信，数据库迁移层 |
| `describeadmin-archetype` | 业务工程脚手架 |

每个模块带来什么、全部配置项、SPI 签名，见文档站的 [参考](https://describeadmin.github.io/docs/reference/starters) 部分。

## 构建

```bash
mvn clean install                                          # 任意 JDK ≥ 17，不需要 toolchains
mvn -Pit -pl framework-it verify                           # 运行时行为门禁（Testcontainers，MySQL 5.7）
mvn -Pit -pl framework-it verify -Dmysql.image=mysql:8.4   # 切到 MySQL 8.4
mvn clean verify -Prelease -Dgpg.skip=true                 # 验证发布产物齐备
```

`framework-it` 是不发布的集成测试模块，只在 `-Pit` 里挂载。

参与开发前读 docs 仓的 [`CLAUDE.md`](https://github.com/describeadmin/docs/blob/main/CLAUDE.md) 与
[开发者文档](https://describeadmin.github.io/docs/dev/)；发布照 [发布手册](https://describeadmin.github.io/docs/dev/release) 执行。

## 变更记录与许可证

[CHANGELOG.md](./CHANGELOG.md) · [Apache-2.0](./LICENSE)
