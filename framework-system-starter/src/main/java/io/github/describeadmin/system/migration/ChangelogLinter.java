package io.github.describeadmin.system.migration;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动时检查 changelog 的两条硬约定，违规直接拒绝启动并指出文件。
 *
 * <p>这两条都是 Liquibase 自己不报错、但后果严重且极隐蔽的坑：
 * <ol>
 *   <li><b>每个文件首行必须声明 {@code logicalFilePath}</b>。changeset 的身份是
 *       {@code id + author + 文件路径}；不固定路径的话，文件挪个目录、或 classpath 的写法变了，
 *       已执行过的 changeset 会被当成新的再执行一遍。</li>
 *   <li><b>带 precondition 的 changeset 必须写 {@code onUpdateSql:TEST}</b>。不写时
 *       {@code update-sql}（{@link MigrationMode#PRINT_SQL}）默认跳过 precondition 检查、
 *       直接把语句写进输出——启动自动迁移时会被正确跳过的「列已存在」，到了交给 DBA 的脚本里
 *       却照常输出，DBA 一执行就报错。注意 formatted SQL 里属性名是 {@code onUpdateSql}，
 *       不是 XML/YAML 里的 {@code onSqlOutput}。</li>
 * </ol>
 */
public class ChangelogLinter {

    /** 框架与业务 changelog 都在这个目录下，见 {@code describeadmin-master.yaml}。 */
    static final String PATTERN = "classpath*:db/changelog/**/*.sql";

    private static final String HEADER = "--liquibase formatted sql";

    public void check(ResourceLoader resourceLoader) {
        ResourcePatternResolver resolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
        List<String> errors = new ArrayList<>();
        try {
            for (Resource resource : resolver.getResources(PATTERN)) {
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                errors.addAll(lint(describe(resource), content));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("读取 changelog 失败", e);
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("changelog 不符合约定，拒绝启动：\n  - "
                    + String.join("\n  - ", errors));
        }
    }

    /** 包内可见，供单元测试直接喂文本。 */
    static List<String> lint(String name, String content) {
        List<String> errors = new ArrayList<>();
        String[] lines = content.replace("﻿", "").split("\\R", -1);
        String first = lines.length == 0 ? "" : lines[0].trim();
        if (!first.startsWith(HEADER) || !first.matches(".*\\blogicalFilePath:\\S+.*")) {
            errors.add(name + "：首行必须是 `" + HEADER + " logicalFilePath:<路径>`");
        }

        String changeset = null;
        boolean hasPrecondition = false;
        boolean testsOnUpdateSql = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("--changeset")) {
                checkPrecondition(errors, name, changeset, hasPrecondition, testsOnUpdateSql);
                changeset = line.substring("--changeset".length()).trim().split("\\s+")[0];
                hasPrecondition = false;
                testsOnUpdateSql = false;
            } else if (line.startsWith("--preconditions")) {
                testsOnUpdateSql = line.contains("onUpdateSql:TEST");
            } else if (line.startsWith("--precondition-")) {
                hasPrecondition = true;
            }
        }
        checkPrecondition(errors, name, changeset, hasPrecondition, testsOnUpdateSql);
        return errors;
    }

    private static void checkPrecondition(List<String> errors, String name, String changeset,
                                          boolean hasPrecondition, boolean testsOnUpdateSql) {
        if (changeset != null && hasPrecondition && !testsOnUpdateSql) {
            errors.add(name + " 的 changeset " + changeset
                    + "：带 precondition 时 `--preconditions` 行必须写 onUpdateSql:TEST");
        }
    }

    private static String describe(Resource resource) {
        try {
            String url = resource.getURL().toString();
            int i = url.lastIndexOf("db/changelog/");
            return i >= 0 ? url.substring(i) : url;
        } catch (IOException e) {
            return resource.getDescription();
        }
    }
}
