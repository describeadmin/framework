package io.github.describeadmin.mybatis.autoconfigure;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.github.describeadmin.common.api.CurrentUserProvider;
import io.github.describeadmin.mybatis.core.AuditMetaObjectHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * framework-mybatis-starter 的自动配置。
 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
@EnableConfigurationProperties(FrameworkMybatisProperties.class)
public class FrameworkMybatisAutoConfiguration {

    /**
     * MyBatis-Plus 拦截器链。
     *
     * <p><b>扩展方式</b>：把自己的 {@code InnerInterceptor} 注册成 Bean 即可，
     * 无需覆盖整个 {@code MybatisPlusInterceptor}——覆盖意味着要把下面这段
     * 分页配置逻辑复制一份，之后框架改了这里，复制出去的那份不会跟着改。
     *
     * <pre>{@code
     * @Bean
     * @Order(0)   // 越小越靠前
     * public InnerInterceptor tenantLineInnerInterceptor() { ... }
     * }</pre>
     *
     * <p><b>顺序</b>：外部注册的拦截器按 {@code @Order} 升序排列，全部排在框架自带的
     * 分页与乐观锁之前。这与 MyBatis-Plus 官方要求的顺序一致——多租户、动态表名、
     * 数据权限这类<b>改写 SQL</b> 的拦截器必须先于分页执行，否则分页的 count 语句
     * 是基于未改写的 SQL 生成的，会统计出被改写条件排除掉的行。
     * 需要排在分页之后的场景实际上只有乐观锁，而它由框架自己持有。
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            FrameworkMybatisProperties properties,
            ObjectProvider<InnerInterceptor> customInterceptors) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // orderedStream 已按 @Order / Ordered 排好序
        customInterceptors.orderedStream().forEach(interceptor::addInnerInterceptor);

        // 分页：方言取自配置，不硬编码，便于切换到国产化数据库
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(properties.getDbType());
        pagination.setMaxLimit(properties.getMaxLimit());
        pagination.setOverflow(properties.isOverflow());
        interceptor.addInnerInterceptor(pagination);

        // 乐观锁，配合 BaseEntity 的 @Version
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }

    /**
     * 审计字段填充器。
     *
     * <p>用 {@code ObjectProvider} 而非直接注入：{@link CurrentUserProvider} 是可选的——
     * 引入了 framework-security-starter 才有实现，只用 ORM 的业务方没有。
     * 缺失时退回 {@link CurrentUserProvider#NOOP}，创建人/更新人留空而不是启动失败。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditMetaObjectHandler auditMetaObjectHandler(
            ObjectProvider<CurrentUserProvider> currentUserProvider) {
        return new AuditMetaObjectHandler(
                currentUserProvider.getIfAvailable(() -> CurrentUserProvider.NOOP));
    }
}
