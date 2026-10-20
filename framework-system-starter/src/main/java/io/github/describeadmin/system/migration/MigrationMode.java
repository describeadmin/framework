package io.github.describeadmin.system.migration;

/**
 * 数据库迁移的执行方式，配置项 {@code describeadmin.migration.mode}。
 *
 * <p>所有环境共用同一套 changelog 和同一张执行记录表 {@code DATABASECHANGELOG}，
 * 所以一个环境用哪种方式只是这一个配置项，随时可以切换。
 */
public enum MigrationMode {

    /** 启动时执行所有未执行的 changeset。只建议在 {@code application-local.yml} 里打开。 */
    APPLY,

    /**
     * 默认值：只检查不执行。有未执行的 changeset、或已执行的 changeset 被改过（校验和不符）
     * 就拒绝启动，并列出缺了哪些——由人决定用 {@link #APPLY} 还是 {@link #PRINT_SQL} 交给 DBA。
     */
    VALIDATE,

    /**
     * 把所有未执行的 changeset 输出成一份 SQL 文件后结束进程，不改库、不启动 Web 服务。
     * 输出里带 {@code DATABASECHANGELOG} 的执行记录 INSERT，DBA 执行完后 {@link #VALIDATE} 即可放行。
     */
    PRINT_SQL,

    /**
     * 只输出执行记录的 INSERT 后结束进程。DBA 用 gh-ost / pt-online-schema-change
     * 这类工具自行改完结构后，执行这份 SQL 把对应 changeset 标记为已执行。
     */
    PRINT_SYNC_SQL,

    /** 完全不检查，逃生口。 */
    OFF
}
