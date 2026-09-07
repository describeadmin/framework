package io.github.describeadmin.system.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.describeadmin.mybatis.api.DataScopeTableCustomizer;
import io.github.describeadmin.security.api.AuthUserLoader;
import io.github.describeadmin.security.api.PasswordPolicy;
import io.github.describeadmin.security.autoconfigure.FrameworkSecurityAutoConfiguration;
import io.github.describeadmin.system.core.DbAuthUserLoader;
import io.github.describeadmin.system.core.DevAdminSeeder;
import io.github.describeadmin.system.core.OperLogAspect;
import io.github.describeadmin.system.mapper.SysRelationMapper;
import io.github.describeadmin.system.service.SysConfigService;
import io.github.describeadmin.system.service.SysOperLogService;
import io.github.describeadmin.system.service.SysRoleService;
import io.github.describeadmin.system.service.SysUserService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * framework-system-starter 的自动配置。
 *
 * <p><b>为什么需要显式 {@code @MapperScan} 与 {@code @ComponentScan}</b>：
 * 业务方应用的 {@code @SpringBootApplication} 只扫描自己的包，
 * 而 {@code @MapperScan("com.业务方...")} 一旦存在，MyBatis 的自动扫描就不再生效。
 * 因此框架必须为自己的 Mapper 与组件登记扫描路径，否则业务方引入 starter 后
 * 会遇到「Bean 找不到」这类难以定位的问题。
 *
 * <p>这两个注解声明的都是框架自己的包，不会侵入业务方的包空间。
 *
 * <p><b>为什么必须声明 {@code before = FrameworkSecurityAutoConfiguration.class}</b>：
 * security-starter 里的内置用户名密码登录带 {@code @ConditionalOnBean(AuthUserLoader.class)}，
 * 而该 Bean 由本类提供。{@code @ConditionalOnBean} 只看【当前已注册】的 Bean 定义，
 * 完全依赖自动配置的评估顺序 —— 若 security 先于 system 被评估，条件不满足，
 * 内置登录方式会被静默跳过，表现为运行时报「不支持的登录方式: password」。
 *
 * <p>这是跨 starter 使用 {@code @ConditionalOnBean} 的典型陷阱：编译期毫无征兆，
 * 只有真实启动上下文才暴露。本项目已由 framework-it 的集成测试捕获过一次。
 */
@AutoConfiguration(before = FrameworkSecurityAutoConfiguration.class)
@ConditionalOnProperty(prefix = "describeadmin.system", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FrameworkSystemProperties.class)
@MapperScan("io.github.describeadmin.system.mapper")
@ComponentScan(basePackages = {
        "io.github.describeadmin.system.service",
        "io.github.describeadmin.system.controller"
})
public class FrameworkSystemAutoConfiguration {

    /**
     * 基于 {@code sys_*} 表的默认用户加载实现。
     *
     * <p>{@code @ConditionalOnMissingBean} 保证业务方自行提供 {@link AuthUserLoader}
     * （例如用户数据来自统一认证中心）时，框架默认实现自动让位。
     */
    @Bean
    @ConditionalOnMissingBean(AuthUserLoader.class)
    public DbAuthUserLoader dbAuthUserLoader(SysUserService userService,
                                             SysRelationMapper relationMapper,
                                             SysConfigService configService) {
        return new DbAuthUserLoader(userService, relationMapper, configService);
    }

    /**
     * 开发种子管理员。默认不装配——只有 {@code application-local.yml} 显式打开
     * {@code describeadmin.system.dev-seed.enabled=true} 时才创建随机口令的管理员账号，
     * 并把明文写到项目根 {@code .passwd}。生产 profile 永远不会有这个 Bean。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "describeadmin.system.dev-seed", name = "enabled",
            havingValue = "true")
    public DevAdminSeeder devAdminSeeder(SysUserService userService, SysRoleService roleService,
                                         PasswordPolicy passwordPolicy,
                                         FrameworkSystemProperties properties) {
        return new DevAdminSeeder(userService, roleService, passwordPolicy, properties.getDevSeed());
    }

    /**
     * 登记 {@code sys_user} 参与数据权限过滤。
     *
     * <p>其余系统管理表（角色/菜单/部门）不登记——它们是权限体系自身的配置数据，
     * 不是"业务数据"，不该被数据权限过滤掉，否则一个只有"本部门"范围的管理员
     * 会连部门树、菜单树都查不全，界面直接坏掉。
     */
    @Bean
    public DataScopeTableCustomizer sysUserDataScopeTableCustomizer() {
        return tableToDeptColumn -> tableToDeptColumn.put("sys_user", "dept_id");
    }

    /**
     * 操作日志切面。
     *
     * <p>不在 {@code @ComponentScan} 的两个基础包（service/controller）里，
     * 与 {@link DbAuthUserLoader} 一样显式 {@code @Bean} 注册——{@code core} 包下的类
     * 一律走这条路径，不悄悄再加一个 basePackage。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "describeadmin.system.oper-log", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public OperLogAspect operLogAspect(SysOperLogService operLogService, ObjectMapper objectMapper) {
        return new OperLogAspect(operLogService, objectMapper);
    }
}
