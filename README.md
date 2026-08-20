# describeadmin framework

describeadmin 的后端框架核心。发布到 Maven Central，groupId `io.github.describeadmin`。

**从零开始用，请看 [快速开始](https://github.com/describeadmin/docs/blob/main/QUICKSTART.md)。**
本文只讲这个仓库本身。

## 引入

只 `import` 一个 BOM，其余模块**一律不写版本号**：

```xml
<properties>
  <describeadmin.version>0.1.0</describeadmin.version>
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

> 别手写这份 POM。以 [sample-app](https://github.com/describeadmin/sample-app)
> 为起点——它已经把三个已实测的接入坑固化成默认正确的状态。

## 模块

| 模块 | 给你什么 |
|---|---|
| `framework-bom` | 统一版本仲裁 |
| `framework-common` | `Result<T>` 统一响应结构、全局异常处理器 |
| `framework-web-starter` | traceId 贯穿请求与日志 |
| `framework-security-starter` | 不透明令牌认证、`AuthProvider` / `TokenStore` SPI、按钮级权限 |
| `framework-mybatis-starter` | `BaseEntity` / `BaseService` / `BaseController` 基类，审计字段与逻辑删除 |
| `framework-system-starter` | 开箱可用的用户 / 角色 / 菜单 / 部门管理 |

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
