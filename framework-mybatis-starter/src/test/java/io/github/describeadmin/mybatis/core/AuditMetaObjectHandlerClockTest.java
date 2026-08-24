package io.github.describeadmin.mybatis.core;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.describeadmin.common.api.CurrentUserProvider;
import io.github.describeadmin.mybatis.api.BaseEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuditMetaObjectHandler} 的时间取自可注入的 {@link Clock}。
 *
 * <p>断言的是<b>具体时刻</b>而不是"时间不为空"（CLAUDE.md 3.6）：只断言非空的话，
 * 就算实现偷偷退回 {@code LocalDateTime.now()} 用例也照样通过，这个测试就白写了。
 *
 * <p>走的是 MyBatis-Plus 真实的填充链路（{@code TableInfo} + {@code strictInsertFill}），
 * 不 mock —— {@code strictInsertFill} 内部依赖 {@code findTableInfo}，
 * 绕过它就等于没有验证真正会执行的那条路径。
 */
@DisplayName("AuditMetaObjectHandler 时钟注入")
class AuditMetaObjectHandlerClockTest {

    private static final LocalDateTime FIXED = LocalDateTime.of(2026, 8, 24, 10, 15, 30);

    private static final Clock FIXED_CLOCK = Clock.fixed(
            FIXED.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AuditedEntity.class);
    }

    @Test
    @DisplayName("insertFill 用注入的时钟，创建时间与更新时间都等于那个固定时刻")
    void insertUsesInjectedClock() {
        AuditedEntity entity = new AuditedEntity();
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        new AuditMetaObjectHandler(() -> 7L, FIXED_CLOCK).insertFill(metaObject);

        assertThat(entity.getCreateTime()).isEqualTo(FIXED);
        assertThat(entity.getUpdateTime()).isEqualTo(FIXED);
        assertThat(entity.getCreateBy()).isEqualTo(7L);
        assertThat(entity.getUpdateBy()).isEqualTo(7L);
        assertThat(entity.getDeleted()).isZero();
        assertThat(entity.getVersion()).isZero();
    }

    @Test
    @DisplayName("updateFill 同样用注入的时钟")
    void updateUsesInjectedClock() {
        AuditedEntity entity = new AuditedEntity();
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        new AuditMetaObjectHandler(() -> 7L, FIXED_CLOCK).updateFill(metaObject);

        assertThat(entity.getUpdateTime()).isEqualTo(FIXED);
        assertThat(entity.getUpdateBy()).isEqualTo(7L);
    }

    @Test
    @DisplayName("时钟推进后填充的时间跟着走，证明确实是从时钟读的")
    void followsClockAdvance() {
        Instant later = FIXED.atZone(ZoneId.systemDefault()).toInstant().plusSeconds(3600);
        AuditedEntity entity = new AuditedEntity();

        new AuditMetaObjectHandler(CurrentUserProvider.NOOP,
                Clock.fixed(later, ZoneId.systemDefault()))
                .insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreateTime()).isEqualTo(FIXED.plusHours(1));
    }

    @Test
    @DisplayName("不传时钟的旧构造函数仍可用——0.1.x 的调用方不受影响")
    void legacyConstructorStillWorks() {
        AuditedEntity entity = new AuditedEntity();

        new AuditMetaObjectHandler(CurrentUserProvider.NOOP)
                .insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getCreateBy()).isNull();
    }

    @TableName("test_audited")
    static class AuditedEntity extends BaseEntity {
    }
}
