# 本工程由 describeadmin-archetype 生成

现在你拿到的是一个**没有任何业务模块、但已经可以登录使用的后台**：
用户 / 角色 / 菜单 / 部门管理与登录接口都在 `framework-system-starter` 里，
不是拷贝进来的代码，升级框架版本就能拿到修复。

> 下面命令里的端口取的是生成时的默认值（应用 8090、MySQL 3307）。
> 如果你生成时改过 `serverPort` / `dbPort`，以 `application-local.yml` 里的值为准。

## 跑起来

```bash
# 1. 数据库（字符集两个参数必须显式给，不能依赖服务器默认值）
docker run -d --name da-mysql -p 3307:3306 \
  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=describeadmin \
  -e MYSQL_USER=app -e MYSQL_PASSWORD=app \
  mysql:5.7 --character-set-server=utf8mb4 --collation-server=utf8mb4_general_ci

# 2. 后端
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

MySQL 5.7 首次启动要 20~30 秒才真正就绪，判断就绪要用**带认证的查询**，
不要用 `mysqladmin ping`（初始化期间的临时服务器会让 ping 立刻成功，但此时 root 口令还没设）：

```bash
docker exec da-mysql mysql -uroot -proot -e 'SELECT 1' && echo 就绪
```

验证登录，令牌在 `data.token`：

```bash
curl -s -X POST http://localhost:8090/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
```

> ⚠️ 默认账号 `admin` / `admin123` 且每次启动重放种子脚本，`local` profile
> **只能用于本地开发**。真实环境另建 `application-dev.yml` / `application-prod.yml`，
> 并把 `spring.sql.init.mode` 设为 `never`。

## 对 JDK 的要求

只有一条：**Maven 进程自身跑在 JDK 17 或更高**。用哪个发行版、哪个大版本随你。

```bash
mvn -v          # 看 Java version 这一行，不要用 java -version
```

`java -version` 看的是 `PATH` 上第一个 java，未必是 Maven 用的那个。
Maven 跑在 JDK 11 上会在打包阶段报一个指向 `RepackageMojo` 的错
（`class file version 61.0 ... up to 55.0`），看起来像 Spring Boot 插件的问题，
实际是 `JAVA_HOME` 太旧。

产物侧由 `pom.xml` 的 `<java.version>17</java.version>` 钉死：无论你用多新的 JDK 构建，
产出的字节码都能在 Java 17 上运行。

## pom.xml 里两行不能删的东西

它们不是风格问题，是两个**已实测的失败**，共同特征是报错与真实原因相距很远：

| 哪一行 | 删了会怎样 |
|---|---|
| `<mysql.version>8.2.0</mysql.version>` | 继承来的 `dependencyManagement` 优先级高于 import 的 BOM，驱动被换成新版，**连 MySQL 5.7 直接失败**。Connector/J 自 8.3.0 起官方不再支持 5.7 |
| `application-local.yml` 的 `encoding: UTF-8` | Spring 用平台默认编码读 SQL 脚本，中文 Windows 上是 GBK，**库里中文全是乱码而 `COUNT(*)` 完全正常** |

第二条引出一条测试纪律：**断言要比对具体值，不要只比对行数**。
`assertThat(count).isEqualTo(1)` 查不出字符集问题，
`assertThat(nickname).isEqualTo("超级管理员")` 才可以。

## 加一个业务模块

用 [codegen](https://github.com/describeadmin/codegen/releases) 从一份 YAML 同时产出
后端四件套 + 建表 SQL + 菜单 SQL + 前端页面：

```bash
java -jar codegen.jar codegen-specs/order.yaml --out .
```

生成后**必须**把两个 SQL 登记进 `application-local.yml` 的
`schema-locations` / `data-locations`，否则页面在侧边栏里不会出现（菜单由后端 `sys_menu` 表下发）。

业务代码写成"薄"的：通用逻辑在框架基类里，审计字段由 `BaseEntity` 承担，不要重复定义。

```java
public class OrderEntity extends BaseEntity { }
public interface OrderMapper extends BaseMapper<OrderEntity> { }
public class OrderService extends BaseService<OrderMapper, OrderEntity> { }
public class OrderController extends BaseController<OrderService, OrderEntity> { }
```

## 升级框架

改 `pom.xml` 里 `<describeadmin.version>` 一行。生成的业务代码不用动。

更多内容见 [describeadmin 文档](https://github.com/describeadmin/docs)。
