package io.github.describeadmin.system.autoconfigure;

import io.github.describeadmin.system.migration.MigrationMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据库迁移的配置项，前缀 {@code describeadmin.migration}。
 *
 * <p>迁移由框架接管（见 {@code DescribeAdminLiquibase}），Spring Boot 自带的
 * {@code spring.liquibase.*} 在这里<b>不生效</b>：开关只有 {@link #mode} 一个，默认只检查不执行。
 */
@ConfigurationProperties(prefix = "describeadmin.migration")
public class MigrationProperties {

    /**
     * 执行方式，默认 {@link MigrationMode#VALIDATE}。
     *
     * <p>默认值刻意选「只检查」：生产库上自动执行 DDL 必须是显式的决定，不能是漏配一行的后果。
     */
    private MigrationMode mode = MigrationMode.VALIDATE;

    /**
     * Liquibase 的运行时 contexts，逗号分隔，<b>不允许为空</b>。
     *
     * <p>Liquibase 在运行时 contexts 为空时会执行<b>所有</b> changeset，包括标了
     * {@code context:local} 的本地数据——这正是 contexts 本该挡住的东西。所以默认给一个
     * 不会被任何 changeset 声明的值 {@code default}，本地 profile 再显式写 {@code local}。
     */
    private String contexts = "default";

    /**
     * {@link MigrationMode#PRINT_SQL} / {@link MigrationMode#PRINT_SYNC_SQL} 的输出文件，
     * 相对启动目录解析。留空则为 {@code migration-<时间戳>.sql}。
     */
    private String outputFile;

    public MigrationMode getMode() {
        return mode;
    }

    public void setMode(MigrationMode mode) {
        this.mode = mode;
    }

    public String getContexts() {
        return contexts;
    }

    public void setContexts(String contexts) {
        this.contexts = contexts;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }
}
