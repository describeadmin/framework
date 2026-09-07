# describeadmin-archetype

业务方后端工程的脚手架。一条命令得到一个**没有任何业务模块、但已经可以登录**的工程：

```bash
mvn archetype:generate -B \
  -DarchetypeGroupId=io.github.describeadmin \
  -DarchetypeArtifactId=describeadmin-archetype \
  -DarchetypeVersion=0.2.1 \
  -DgroupId=com.acme \
  -DartifactId=order-server \
  -Dpackage=com.acme.order
```

`archetypeVersion` 用最新的已发布版本（以 Maven Central 为准）——archetype 与框架同号发布，
它生成的工程引用的就是这个框架版本。去掉 `-B` 进入交互模式，会逐项询问
`archetype-metadata.xml` 里声明的参数。

## 它交付的是什么

不是"少敲几行 XML"，而是把两个**已实测的接入失败**固化成默认正确的初始状态。
这两条的共同特征是报错信息与真实原因相距很远，靠读文档发现不了：

| 生成物里的哪一行 | 缺了会怎样 |
|---|---|
| `pom.xml` 的 `<mysql.version>8.2.0</mysql.version>` | 继承来的 `dependencyManagement` 优先级高于 import 的 BOM，驱动被换成新版，连 MySQL 5.7 直接失败 |
| `application-local.yml` 的 `encoding: UTF-8` | Spring 用平台默认编码读 SQL 脚本，中文全乱码而 `COUNT(*)` 完全正常 |

生成物**不带任何 SQL 文件**：`schema-rbac.sql` / `seed-rbac.sql` 在
`framework-system-starter` 的 jar 里，业务方通过 `classpath:` 引用即可。
这正是"空工程也能直接登录"的原因。

## 为什么不生成 toolchains 配置

早期的样板工程 POM 里配过 `maven-toolchains-plugin`，本脚手架**刻意不带**。

框架自身构建也只要求 JDK 17+（同样不再用 toolchains，见 `develop_plan.md` 2.2.2「第八轮修订」）。
业务方的约束同样只有一条：**Maven 进程自身跑在 JDK 17+**
（`spring-boot-maven-plugin:repackage` 的硬要求）。至于用哪个发行版、哪个大版本，
是业务方的自由 —— 产物侧已由 `<java.version>17</java.version>`（即 `release=17`）钉死，
无论用多新的 JDK 构建，字节码都落在 Java 17。

把 toolchains 抄进生成物会带来一个更糟的后果：业务方开发机上大多没有
`~/.m2/toolchains.xml`，插件会以 `Cannot find matching toolchain` 直接打死构建 ——
比它要防的那个坑更劝退。

## 维护须知

### 版本与框架强绑定

`archetype-metadata.xml` 里 `describeadminVersion` 的默认值写的是 `${project.version}`，
在**构建期**由资源过滤替换成本模块版本号。因此 archetype 的版本 == 它生成的工程引用的框架版本，
不存在手工同步的窗口 —— 但也意味着**本模块必须跟着框架一起发版**，不能单独演进。

### 三个改模板时必踩的坑

| 坑 | 症状 | 处置 |
|---|---|---|
| 过滤边界 | 模板里的 `${...}` 被提前掏空，生成物里是空值 | `archetype-resources/**` 绝不能开资源过滤；只过滤 `META-INF/maven/**` |
| Velocity 把 `##` 当行注释 | markdown 的二级标题在生成物里**整行消失，不报任何错** | 不需要变量替换的文件（README、点文件）在描述符里就不要标 `filtered` |
| Maven 默认排除点文件 | `.gitignore` / `.gitattributes` 进不了 jar，生成物里没有 | `maven-resources-plugin` 配 `addDefaultExcludes=false`（配进 `<resource>` 会报 `Unrecognised tag`） |

### 自带的集成测试

`src/test/resources/projects/basic/` 是 `maven-archetype-plugin` 的标准 IT：
`mvn verify` 时自动用 `archetype.properties` 里的参数生成一个工程，
并对它执行 `goal.txt` 里的 goal。

其中 `describeadminVersion` 刻意钉死为**已发布到 Central 的版本**而非 `${project.version}`：
IT 跑在 `integration-test` 阶段，此时本次构建的框架模块还没 install 到本地仓库，
若生成物引用 `0.1.1-SNAPSHOT` 之类，Maven 连模型都建不起来。发新版时手工更新这一行。

真正的端到端验证（生成 → 构建 → 起服务 → 登录 → 核验中文字节）在 CI 的
`archetype-e2e` job 里，见 `.github/workflows/ci.yml`。
