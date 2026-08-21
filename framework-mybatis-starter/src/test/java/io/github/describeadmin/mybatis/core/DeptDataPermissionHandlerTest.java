package io.github.describeadmin.mybatis.core;

import io.github.describeadmin.common.api.DataScopeContext;
import io.github.describeadmin.common.api.DataScopeProvider;
import io.github.describeadmin.common.api.DataScopeType;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DeptDataPermissionHandler} 按数据权限范围生成 SQL 条件的单元测试。
 *
 * <p>不起 Spring 上下文，用字面量 Map 与假 {@link DataScopeProvider} 直接构造被测对象——
 * 这是纯粹的字符串拼装逻辑，值得用最快的方式覆盖每一档范围。
 */
@DisplayName("DeptDataPermissionHandler 生成的过滤条件")
class DeptDataPermissionHandlerTest {

    private static final Table SYS_USER = new Table("sys_user");
    private static final Table SYS_ROLE = new Table("sys_role");

    @Test
    @DisplayName("未登记的表不受影响，返回 null")
    void unregisteredTableReturnsNull() {
        DeptDataPermissionHandler handler = handler(Map.of("sys_user", "dept_id"),
                fixed(ctx(DataScopeType.DEPT)));
        assertThat(handler.getSqlSegment(SYS_ROLE, null, "any")).isNull();
    }

    @Test
    @DisplayName("无登录上下文（如系统内部调用）不过滤")
    void noContextReturnsNull() {
        DeptDataPermissionHandler handler = handler(Map.of("sys_user", "dept_id"), DataScopeProvider.NOOP);
        assertThat(handler.getSqlSegment(SYS_USER, null, "any")).isNull();
    }

    @Test
    @DisplayName("ALL 档不过滤")
    void allScopeReturnsNull() {
        DeptDataPermissionHandler handler = registeredHandler(fixed(ctx(DataScopeType.ALL)));
        assertThat(handler.getSqlSegment(SYS_USER, null, "any")).isNull();
    }

    @Test
    @DisplayName("DEPT 档：等于当前用户部门")
    void deptScope() {
        DeptDataPermissionHandler handler = registeredHandler(fixed(ctx(DataScopeType.DEPT)));
        assertThat(handler.getSqlSegment(SYS_USER, null, "any").toString())
                .isEqualTo("dept_id = 5");
    }

    @Test
    @DisplayName("DEPT_AND_CHILD 档：本部门及以下，走 ancestors 子查询")
    void deptAndChildScope() {
        DeptDataPermissionHandler handler = registeredHandler(fixed(ctx(DataScopeType.DEPT_AND_CHILD)));
        assertThat(handler.getSqlSegment(SYS_USER, null, "any").toString())
                .isEqualTo("dept_id IN (SELECT id FROM sys_dept WHERE id = 5 OR FIND_IN_SET(5, ancestors))");
    }

    @Test
    @DisplayName("CUSTOM 档：命中角色配置的部门列表")
    void customScope() {
        Set<Long> deptIds = new LinkedHashSet<>(java.util.List.of(3L, 7L, 9L));
        DeptDataPermissionHandler handler = registeredHandler(
                fixed(new DataScopeContext(1L, 5L, DataScopeType.CUSTOM, deptIds)));
        assertThat(handler.getSqlSegment(SYS_USER, null, "any").toString())
                .isEqualTo("dept_id IN (3, 7, 9)");
    }

    @Test
    @DisplayName("CUSTOM 档但一个部门都没配：看不见任何数据，而不是退化成看得见一切")
    void customScopeWithoutAnyDept() {
        DeptDataPermissionHandler handler = registeredHandler(
                fixed(new DataScopeContext(1L, 5L, DataScopeType.CUSTOM, Set.of())));
        assertThat(handler.getSqlSegment(SYS_USER, null, "any").toString()).isEqualTo("1 = 0");
    }

    @Test
    @DisplayName("SELF 档：只看自己创建的数据，复用 BaseEntity 的通用 create_by 列")
    void selfScope() {
        DeptDataPermissionHandler handler = registeredHandler(
                fixed(new DataScopeContext(42L, 5L, DataScopeType.SELF, Set.of())));
        assertThat(handler.getSqlSegment(SYS_USER, null, "any").toString())
                .isEqualTo("create_by = 42");
    }

    // ---------------------------------------------------------------- helpers

    private static DeptDataPermissionHandler registeredHandler(DataScopeProvider provider) {
        return handler(Map.of("sys_user", "dept_id"), provider);
    }

    private static DeptDataPermissionHandler handler(Map<String, String> tableToDeptColumn,
                                                      DataScopeProvider provider) {
        return new DeptDataPermissionHandler(tableToDeptColumn, provider);
    }

    private static DataScopeContext ctx(DataScopeType type) {
        return new DataScopeContext(1L, 5L, type, Set.of());
    }

    private static DataScopeProvider fixed(DataScopeContext ctx) {
        return () -> Optional.of(ctx);
    }
}
