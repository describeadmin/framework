package io.github.describeadmin.system.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link ChangelogLinter} 的两条约定。纯文本输入，不起 Spring 上下文、不连库。
 */
@DisplayName("ChangelogLinter changelog 约定检查")
class ChangelogLinterTest {

    private static final String HEADER = "--liquibase formatted sql logicalFilePath:app/x.sql\n";

    @Test
    @DisplayName("合规文件没有错误")
    void compliant() {
        String sql = HEADER + """
                --changeset app:t1
                CREATE TABLE IF NOT EXISTS t1 (id BIGINT);

                --changeset app:t1-c
                --preconditions onFail:MARK_RAN onUpdateSql:TEST
                --precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE 1=0
                ALTER TABLE t1 ADD COLUMN c INT;
                """;
        assertThat(ChangelogLinter.lint("x.sql", sql)).isEmpty();
    }

    @Test
    @DisplayName("首行缺 logicalFilePath：文件挪位置后已执行的 changeset 会被重复执行")
    void missingLogicalFilePath() {
        assertThat(ChangelogLinter.lint("x.sql", "--liquibase formatted sql\n--changeset app:a\nSELECT 1;"))
                .singleElement().asString().contains("logicalFilePath");
        assertThat(ChangelogLinter.lint("x.sql", "-- 注释在前\n--liquibase formatted sql logicalFilePath:a\n"))
                .singleElement().asString().contains("首行");
    }

    @Test
    @DisplayName("BOM 不影响首行判断")
    void bomTolerated() {
        assertThat(ChangelogLinter.lint("x.sql", "﻿" + HEADER)).isEmpty();
    }

    @Test
    @DisplayName("precondition 不写 onUpdateSql:TEST：print-sql 会把本该跳过的 ALTER 输出给 DBA")
    void preconditionWithoutUpdateSqlTest() {
        String sql = HEADER + """
                --changeset app:ok
                SELECT 1;

                --changeset app:bad
                --preconditions onFail:MARK_RAN
                --precondition-sql-check expectedResult:0 SELECT 1
                ALTER TABLE t ADD COLUMN c INT;

                --changeset app:none
                --precondition-sql-check expectedResult:0 SELECT 1
                ALTER TABLE t ADD COLUMN d INT;
                """;
        assertThat(ChangelogLinter.lint("x.sql", sql))
                .hasSize(2)
                .anySatisfy(e -> assertThat(e).contains("app:bad"))
                .anySatisfy(e -> assertThat(e).contains("app:none"));
    }

    @Test
    @DisplayName("框架自带的 changelog 本身合规")
    void frameworkChangelogsCompliant() {
        assertThatCode(() -> new ChangelogLinter().check(new DefaultResourceLoader()))
                .doesNotThrowAnyException();
    }
}
