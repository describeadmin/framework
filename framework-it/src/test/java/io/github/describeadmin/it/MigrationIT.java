package io.github.describeadmin.it;

import io.github.describeadmin.system.autoconfigure.MigrationProperties;
import io.github.describeadmin.system.migration.DescribeAdminLiquibase;
import io.github.describeadmin.system.migration.MigrationMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 数据库迁移层（{@link DescribeAdminLiquibase}）的四种模式。
 *
 * <p>不走 Spring 上下文：每个用例在同一个 MySQL 容器里新建一个空库，直接构造迁移 Bean 跑一遍，
 * 这样「空库 / 存量库 / 已迁移库」几种起点可以随意摆，互不污染，也不影响其他 IT 共享的
 * {@code describeadmin_test} 库。
 *
 * <p>交给 DBA 这条路的核心保证由这里钉住：{@code print-sql} 生成的脚本执行后，
 * {@code validate} 能放行——执行记录与真实结构一致。
 */
@DisplayName("数据库迁移")
class MigrationIT extends AbstractMySqlIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @TempDir
    Path tmp;

    @Test
    @DisplayName("apply：框架 changeset 先于业务执行，中文按字节正确")
    void applyOrdersFrameworkFirstAndKeepsChinese() throws Exception {
        DataSource ds = newSchema();
        run(ds, MigrationMode.APPLY, null);
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        List<String> files = jdbc.queryForList(
                "SELECT FILENAME FROM DATABASECHANGELOG ORDER BY ORDEREXECUTED", String.class);
        int lastFramework = -1;
        int firstApp = Integer.MAX_VALUE;
        for (int i = 0; i < files.size(); i++) {
            if (files.get(i).startsWith("describeadmin/")) {
                lastFramework = i;
            } else if (files.get(i).startsWith("app/")) {
                firstApp = Math.min(firstApp, i);
            }
        }
        assertThat(firstApp).as("业务 changeset 被 includeAll 扫到了").isLessThan(Integer.MAX_VALUE);
        assertThat(lastFramework).isLessThan(firstApp);

        // 只比行数查不出编码问题，必须比字节
        String hex = jdbc.queryForObject(
                "SELECT HEX(role_name) FROM sys_role WHERE role_code = 'ADMIN'", String.class);
        assertThat(hex).isEqualToIgnoringCase(
                HexFormat.of().formatHex("超级管理员".getBytes(StandardCharsets.UTF_8)));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE perm_code = 'project:list' AND deleted = 0", Integer.class))
                .isEqualTo(1);

        // 再跑一次是空操作
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu", Integer.class);
        run(ds, MigrationMode.APPLY, null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu", Integer.class)).isEqualTo(before);
    }

    @Test
    @DisplayName("validate：有未执行的 changeset 拒绝启动，并且不改库")
    void validateRejectsPendingWithoutTouchingDb() {
        DataSource ds = newSchema();
        assertThatThrownBy(() -> run(ds, MigrationMode.VALIDATE, null))
                .hasStackTraceContaining("未执行的 changeset")
                .hasStackTraceContaining("describeadmin/0001-rbac-schema.sql::sys_user::describeadmin")
                .hasStackTraceContaining("print-sql");
        assertThat(tables(ds)).as("只检查，不应建任何表").isEmpty();
    }

    @Test
    @DisplayName("print-sql：不改库；DBA 执行输出后 validate 放行")
    void printSqlThenValidatePasses() throws Exception {
        DataSource ds = newSchema();
        Path out = tmp.resolve("pending.sql");
        AtomicInteger exit = new AtomicInteger(-1);
        run(ds, MigrationMode.PRINT_SQL, out, exit);

        assertThat(exit.get()).as("打印模式写完即退出").isEqualTo(0);
        assertThat(tables(ds)).as("print-sql 不改库").isEmpty();
        String sql = Files.readString(out, StandardCharsets.UTF_8);
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS sys_user", "INSERT INTO", "DATABASECHANGELOG");

        executeAsDba(ds, out);
        run(ds, MigrationMode.VALIDATE, null);
        assertThat(new JdbcTemplate(ds).queryForObject(
                "SELECT HEX(role_name) FROM sys_role WHERE role_code = 'ADMIN'", String.class))
                .isEqualToIgnoringCase(HexFormat.of().formatHex("超级管理员".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("print-sync-sql：只输出执行记录，供 DBA 用自己的工具改完结构后标记")
    void printSyncSqlOnlyWritesLedger() throws Exception {
        DataSource ds = legacySchema();
        Path out = tmp.resolve("sync.sql");
        run(ds, MigrationMode.PRINT_SYNC_SQL, out, new AtomicInteger());
        String sql = Files.readString(out, StandardCharsets.UTF_8);
        assertThat(sql).contains("DATABASECHANGELOG").doesNotContain("CREATE TABLE IF NOT EXISTS sys_user");

        executeAsDba(ds, out);
        run(ds, MigrationMode.VALIDATE, null);
    }

    @Test
    @DisplayName("已执行的 DDL changeset 被改过（校验和不符）：validate 拒绝启动")
    void checksumMismatchRejected() throws Exception {
        DataSource ds = newSchema();
        run(ds, MigrationMode.APPLY, null);
        new JdbcTemplate(ds).update("UPDATE DATABASECHANGELOG SET MD5SUM = '9:00000000000000000000000000000000' "
                + "WHERE ID = 'sys_user' AND AUTHOR = 'describeadmin'");
        assertThatThrownBy(() -> run(ds, MigrationMode.VALIDATE, null))
                .hasStackTraceContaining("sys_user");
    }

    // ---------------------------------------------------------------- helpers

    private void run(DataSource ds, MigrationMode mode, Path out) throws Exception {
        run(ds, mode, out, new AtomicInteger());
    }

    private void run(DataSource ds, MigrationMode mode, Path out, AtomicInteger exit) throws Exception {
        MigrationProperties props = new MigrationProperties();
        props.setMode(mode);
        props.setContexts("local");
        if (out != null) {
            props.setOutputFile(out.toString());
        }
        DescribeAdminLiquibase liquibase = new DescribeAdminLiquibase(props);
        liquibase.setDataSource(ds);
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.setBeanName("migrationIT");
        liquibase.setExitHandler(exit::set);
        liquibase.afterPropertiesSet();
    }

    /** 同一个容器里新建一个空库。root 口令与 app 用户相同（Testcontainers 的约定）。 */
    private DataSource newSchema() {
        String name = "mig_" + SEQ.incrementAndGet() + "_" + System.nanoTime() % 100000;
        JdbcTemplate root = new JdbcTemplate(dataSource("mysql", "root"));
        root.execute("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        return dataSource(name, "root");
    }

    /** 先完整迁移一遍再删掉执行记录表，等价于结构已由 DBA 用别的工具建好、还没有执行记录的库。 */
    private DataSource legacySchema() throws Exception {
        DataSource ds = newSchema();
        run(ds, MigrationMode.APPLY, null);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP TABLE DATABASECHANGELOG");
        jdbc.execute("DROP TABLE DATABASECHANGELOGLOCK");
        return ds;
    }

    private static DataSource dataSource(String schema, String user) {
        String url = MYSQL.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + schema + "$1");
        return new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?")
                + "useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8",
                user, MYSQL.getPassword());
    }

    private static List<String> tables(DataSource ds) {
        return new JdbcTemplate(ds).queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", String.class);
    }

    /** 像 DBA 一样执行生成的脚本：按分号切分、跳过注释。 */
    private static void executeAsDba(DataSource ds, Path script) throws Exception {
        try (Connection c = ds.getConnection()) {
            ScriptUtils.executeSqlScript(c, new EncodedResource(new FileSystemResource(script), StandardCharsets.UTF_8));
        }
    }
}
