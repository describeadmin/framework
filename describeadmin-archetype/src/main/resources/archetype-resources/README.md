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

首次启动时 `dev-seed` 会创建管理员 `admin`，**口令随机生成**——看启动日志里
`dev-seed 生成初始管理员` 那几行，或读项目根目录的 `.passwd` 文件（已在 `.gitignore` 中）。

验证登录（把 `<.passwd 里的口令>` 换成实际值），令牌在 `data.token`：

```bash
curl -s -X POST http://localhost:8090/api/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"$(cat .passwd)\"}"
```

> ⚠️ `local` profile 启动时自动执行数据库迁移、并用 `dev-seed` 建随机口令的管理员，
> **只能用于本地开发**。真实环境另建 `application-dev.yml` / `application-prod.yml`，
> **不要**打开 `describeadmin.system.dev-seed`；迁移怎么做见下面「上生产」一节。
> 管理员为其他用户建号 / 重置密码后，对方首次登录会被要求先改密。

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

## pom.xml 里不能删的一行

`<mysql.version>8.2.0</mysql.version>` 不是风格问题，是一个**已实测的失败**：
继承来的 `dependencyManagement` 优先级高于 import 的 BOM，驱动被换成新版，
**连 MySQL 5.7 直接失败**（Connector/J 自 8.3.0 起官方不再支持 5.7），而报错与这个原因相距很远。

顺带一条测试纪律：**断言要比对具体值，不要只比对行数**。
`assertThat(count).isEqualTo(1)` 查不出字符集问题，
`assertThat(nickname).isEqualTo("超级管理员")` 才可以。

## 加一个业务模块

用 [codegen](https://github.com/describeadmin/codegen/releases) 从一份 YAML 同时产出
后端四件套 + 数据库变更 + 前端页面：

```bash
java -jar codegen.jar codegen-specs/order.yaml --out .
```

数据库变更是 `src/main/resources/db/changelog/app/<时间戳>__<表名>.sql`（Liquibase formatted SQL，
建表与菜单都在里面），重启后由框架自动执行，**不需要在任何地方登记**。
之后要加列 / 改列，在同一目录**新建**一个 `<yyyyMMddHHmm>__<描述>.sql`，不要改已执行过的建表语句——
改了的话校验和对不上，所有已建库的环境都会拒绝启动。

业务代码写成"薄"的：通用逻辑在框架基类里，审计字段由 `BaseEntity` 承担，不要重复定义。

```java
public class OrderEntity extends BaseEntity { }
public interface OrderMapper extends BaseMapper<OrderEntity> { }
public class OrderService extends BaseService<OrderMapper, OrderEntity> { }
public class OrderController extends BaseController<OrderService, OrderEntity> { }
```

## 上生产

`describeadmin.migration.mode` 不配置时是 `validate`：启动时只检查，有未执行的数据库变更就拒绝启动，
并列出缺了哪些。两种处理方式：

- **交给 DBA**：在能连生产库的机器上，用同一个 jar 加生产配置执行
  `java -jar app.jar --spring.profiles.active=prod --describeadmin.migration.mode=print-sql`，
  生成待执行的 SQL 后进程即退出、不改库。DBA 审阅并执行后，正常启动即可。
- **由应用执行**：用 `--describeadmin.migration.mode=apply` 启动一次。

## 升级框架

改 `pom.xml` 里 `<describeadmin.version>` 一行。生成的业务代码不用动。
框架自己的表结构变更随框架 jar 一起带来，和业务变更走同一套迁移流程，不需要再手动执行 ALTER。

更多内容见 [describeadmin 文档](https://github.com/describeadmin/docs)。
