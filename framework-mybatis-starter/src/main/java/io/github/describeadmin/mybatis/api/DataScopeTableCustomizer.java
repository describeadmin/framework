package io.github.describeadmin.mybatis.api;

import java.util.Map;

/**
 * 登记某张表参与数据权限过滤。
 *
 * <p>数据权限拦截器按表名决定要不要给一条 SQL 注入过滤条件——<b>默认不参与</b>，
 * 业务方的表不会被意外过滤。想让某个实体参与，注册一个本接口的 Bean：
 *
 * <pre>{@code
 * @Bean
 * public DataScopeTableCustomizer projectDataScope() {
 *     return tableToDeptColumn -> tableToDeptColumn.put("biz_project", "dept_id");
 * }
 * }</pre>
 *
 * <p><b>为什么不用注解 + 反射扫描</b>：数据权限拦截器构建时机早于 MyBatis-Plus 把全部实体
 * 登记进 {@code TableInfoHelper} 的时机，靠反射扫描意味着要处理这个时序问题；
 * 而 {@code FrameworkMybatisAutoConfiguration} 已经用同一种
 * {@code ObjectProvider<InnerInterceptor>} 收集手法解决过类似的"核心定义扩展点、
 * 使用方登记内容"问题（见该类收集自定义拦截器的写法），直接复用即可，
 * 不必再发明一套注解扫描机制。
 *
 * <p>本接口位于 {@code api} 包下，属于兼容性承诺范围。
 */
@FunctionalInterface
public interface DataScopeTableCustomizer {

    /**
     * @param tableToDeptColumn 表名 → 部门列名 的可变 Map，直接往里写即可
     */
    void customize(Map<String, String> tableToDeptColumn);
}
