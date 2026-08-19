package io.github.describeadmin.mybatis.core;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充。
 *
 * <p>配合 {@link io.github.describeadmin.mybatis.api.BaseEntity} 上的
 * {@code @TableField(fill = ...)}，业务代码不需要手工设置创建/更新人与时间。
 *
 * <p>当前用户 ID 的获取通过 {@link CurrentUserProvider} 解耦——本模块不依赖
 * framework-security-starter，否则只用 ORM 不用鉴权的场景会被迫拖进 Spring Security。
 * 未提供实现时审计人字段留空，不影响其余功能。
 */
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private final CurrentUserProvider currentUserProvider;

    public AuditMetaObjectHandler() {
        this(CurrentUserProvider.NOOP);
    }

    public AuditMetaObjectHandler(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider == null ? CurrentUserProvider.NOOP : currentUserProvider;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = currentUserProvider.currentUserId();

        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "deleted", Integer.class, 0);
        strictInsertFill(metaObject, "version", Integer.class, 0);
        if (userId != null) {
            strictInsertFill(metaObject, "createBy", Long.class, userId);
            strictInsertFill(metaObject, "updateBy", Long.class, userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        Long userId = currentUserProvider.currentUserId();
        if (userId != null) {
            strictUpdateFill(metaObject, "updateBy", Long.class, userId);
        }
    }
}
