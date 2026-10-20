package io.github.describeadmin.system.migration;

import io.github.describeadmin.system.autoconfigure.MigrationProperties;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.filter.ContextChangeSetFilter;
import liquibase.changelog.filter.DbmsChangeSetFilter;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.snapshot.SnapshotGeneratorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/**
 * 框架接管的 Liquibase：按 {@code describeadmin.migration.mode} 决定启动时做什么。
 *
 * <p><b>为什么继承 {@link SpringLiquibase} 而不是用 Spring Boot 的自动配置</b>：
 * <ul>
 *   <li>classpath 上一出现 Liquibase，Spring Boot 默认 {@code spring.liquibase.enabled=true}，
 *       即生产环境默认启动就执行 DDL。容器里有了本类这个 {@link SpringLiquibase} Bean，
 *       {@code LiquibaseAutoConfiguration} 因 {@code @ConditionalOnMissingBean} 整体退出，
 *       写了 {@code spring.liquibase.enabled=true} 也不生效——开关只剩一个，默认只检查。</li>
 *   <li>Spring Boot 把 {@link SpringLiquibase} 类型的 Bean 识别为数据库初始化器，
 *       依赖数据库的 Bean 排在它后面：每种模式都在应用对外服务之前完成，
 *       不存在「先用旧结构服务一会儿，再发现缺版本」的窗口。</li>
 * </ul>
 *
 * <p>连接获取、Liquibase 的 Scope 设置都复用父类的 {@code afterPropertiesSet}，
 * 这里只替换最后一步 {@link #performUpdate}。
 */
public class DescribeAdminLiquibase extends SpringLiquibase {

    /** 主 changelog：先框架自己的表，再 {@code includeAll} 业务方的 {@code db/changelog/app/}。 */
    public static final String MASTER_CHANGELOG = "classpath:db/changelog/describeadmin-master.yaml";

    private static final Logger logger = LoggerFactory.getLogger(DescribeAdminLiquibase.class);

    private final MigrationProperties properties;
    private final ChangelogLinter linter = new ChangelogLinter();

    /** 打印模式写完文件后结束进程。可替换，供集成测试在同一个 JVM 里验证输出。 */
    private IntConsumer exitHandler = System::exit;

    private Path printedFile;

    public DescribeAdminLiquibase(MigrationProperties properties) {
        this.properties = properties;
        setChangeLog(MASTER_CHANGELOG);
        setContexts(properties.getContexts());
        // Liquibase 4.30 起默认会上报匿名使用数据；框架跑在业务方的生产服务器上，不该有这种外联
        setAnalyticsEnabled(false);
    }

    public void setExitHandler(IntConsumer exitHandler) {
        this.exitHandler = exitHandler;
    }

    @Override
    public void afterPropertiesSet() throws LiquibaseException {
        MigrationMode mode = properties.getMode();
        if (mode == MigrationMode.OFF) {
            logger.warn("describeadmin.migration.mode=off：不检查数据库结构是否为最新");
            return;
        }
        linter.check(resourceLoader);
        super.afterPropertiesSet();

        if (printedFile != null) {
            logger.warn("已把待执行的 SQL 写到 {}，进程退出（{} 模式不启动服务）",
                    printedFile.toAbsolutePath(), mode.name().toLowerCase().replace('_', '-'));
            exitHandler.accept(0);
        }
    }

    @Override
    protected void performUpdate(Liquibase liquibase) throws LiquibaseException {
        switch (properties.getMode()) {
            case APPLY -> super.performUpdate(liquibase);
            case VALIDATE -> validate(liquibase);
            case PRINT_SQL, PRINT_SYNC_SQL -> print(liquibase);
            case OFF -> { }
        }
    }

    private void validate(Liquibase liquibase) throws LiquibaseException {
        Contexts contexts = new Contexts(getContexts());
        List<ChangeSet> pending;
        if (SnapshotGeneratorFactory.getInstance().hasDatabaseChangeLogTable(liquibase.getDatabase())) {
            // 校验和不符（已执行的 changeset 被改过）、changeset 标识重复等，在这一步报错
            liquibase.validate();
            pending = liquibase.listUnrunChangeSets(contexts, new LabelExpression(getLabelFilter()), false);
        } else {
            // 库里还没有执行记录表，所有 changeset 都未执行。这里不能调 validate() /
            // listUnrunChangeSets()：它们会顺手把 DATABASECHANGELOG 建出来，而 validate 承诺不改库
            // ——DBA 管的生产库上，应用账号也未必有建表权限。
            ContextChangeSetFilter contextFilter = new ContextChangeSetFilter(contexts);
            DbmsChangeSetFilter dbmsFilter = new DbmsChangeSetFilter(liquibase.getDatabase());
            pending = liquibase.getDatabaseChangeLog().getChangeSets().stream()
                    .filter(cs -> contextFilter.accepts(cs).isAccepted() && dbmsFilter.accepts(cs).isAccepted())
                    .toList();
        }
        if (pending.isEmpty()) {
            logger.info("数据库结构已是最新（describeadmin.migration.mode=validate）");
            return;
        }
        String list = pending.stream()
                .map(cs -> cs.getFilePath() + "::" + cs.getId() + "::" + cs.getAuthor())
                .collect(Collectors.joining("\n  - "));
        throw new LiquibaseException("数据库有 " + pending.size() + " 个未执行的 changeset，拒绝启动：\n  - "
                + list + "\n处理方式（二选一）："
                + "\n  1. 交给 DBA：用 --describeadmin.migration.mode=print-sql 生成待执行的 SQL，"
                + "审阅并执行后重新启动；"
                + "\n  2. 由应用执行：用 --describeadmin.migration.mode=apply 启动一次。");
    }

    private void print(Liquibase liquibase) throws LiquibaseException {
        Path file = outputFile();
        Contexts contexts = new Contexts(getContexts());
        LabelExpression labels = new LabelExpression(getLabelFilter());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            if (properties.getMode() == MigrationMode.PRINT_SQL) {
                liquibase.update(contexts, labels, writer);
            } else {
                liquibase.changeLogSync(contexts, labels, writer);
            }
        } catch (IOException e) {
            throw new LiquibaseException("写入 " + file.toAbsolutePath() + " 失败", e);
        }
        printedFile = file;
    }

    private Path outputFile() {
        String configured = properties.getOutputFile();
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return Path.of("migration-" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".sql");
    }
}
