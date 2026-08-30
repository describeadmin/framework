package io.github.describeadmin.mybatis.autoconfigure;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.github.describeadmin.common.api.CurrentUserProvider;
import io.github.describeadmin.common.api.DataScopeProvider;
import io.github.describeadmin.mybatis.api.DataScopeTableCustomizer;
import io.github.describeadmin.mybatis.core.AuditMetaObjectHandler;
import io.github.describeadmin.mybatis.core.DeptDataPermissionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

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
     * 数据权限拦截器。
     *
     * <p>本身就是一个 {@link InnerInterceptor}，会被上面 {@code mybatisPlusInterceptor()}
     * 里已有的 {@code ObjectProvider<InnerInterceptor>} 自动收集——那个方法完全不用改，
     * 这正是 0.2.0 预留那条扩展缝的意义。
     *
     * <p>表要不要参与过滤靠 {@link DataScopeTableCustomizer} 显式登记（见
     * {@code FrameworkSystemAutoConfiguration} 里给 {@code sys_user} 登记的例子），
     * 未登记的表不受影响。{@link DataScopeProvider} 缺失（未引入
     * framework-security-starter）时退回 {@link DataScopeProvider#NOOP}，等同于不过滤。
     *
     * <p>不加 {@code @ConditionalOnMissingBean}：{@code InnerInterceptor} 允许同时存在多个
     * （多租户、数据权限等各自注册一个），按类型"缺失才生效"的条件在这里没有意义，
     * 想禁用只需要关掉下面的配置开关。
     */
    @Bean
    @ConditionalOnProperty(prefix = "describeadmin.mybatis.data-scope", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @Order(0)
    public InnerInterceptor dataPermissionInnerInterceptor(
            ObjectProvider<DataScopeTableCustomizer> tableCustomizers,
            ObjectProvider<DataScopeProvider> dataScopeProvider) {
        Map<String, String> tableToDeptColumn = new HashMap<>();
        tableCustomizers.orderedStream().forEach(customizer -> customizer.customize(tableToDeptColumn));
        DeptDataPermissionHandler handler = new DeptDataPermissionHandler(
                tableToDeptColumn, dataScopeProvider.getIfAvailable(() -> DataScopeProvider.NOOP));
        return new DataPermissionInterceptor(handler);
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
            ObjectProvider<CurrentUserProvider> currentUserProvider,
            ObjectProvider<Clock> clock) {
        return new AuditMetaObjectHandler(
                currentUserProvider.getIfAvailable(() -> CurrentUserProvider.NOOP),
                clock.getIfAvailable(Clock::systemDefaultZone));
    }

    /**
     * 框架的时钟。业务方定义自己的 {@code Clock} Bean 即可覆盖，测试里换成
     * {@code Clock.fixed(...)} 就能让审计时间可断言。
     *
     * <p>放在本模块而不是上提到某个公共自动配置，是因为目前只有审计填充这一个消费方。
     * 出现第二个消费方时再考虑上提——不为一个用途预先造一个
     * "framework-core-autoconfigure" 模块。
     */
    @Bean
    @ConditionalOnMissingBean
    public Clock frameworkClock() {
        return Clock.systemDefaultZone();
    }
}
