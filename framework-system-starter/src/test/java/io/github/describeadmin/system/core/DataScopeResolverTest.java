package io.github.describeadmin.system.core;

import io.github.describeadmin.common.api.DataScopeType;
import io.github.describeadmin.system.mapper.RoleScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DataScopeResolver} 多角色数据权限范围合并的单元测试。
 *
 * <p>纯函数，不起 Spring 上下文。合并规则错了的后果是"某个用户看到了不该看到的数据"，
 * 比权限点推导错误更隐蔽——后者至少会直接 403，这里没有等价的显性失败信号，
 * 因此边界必须逐一钉死。
 */
@DisplayName("DataScopeResolver 多角色范围合并")
class DataScopeResolverTest {

    @Test
    @DisplayName("单角色直接生效")
    void singleRole() {
        assertThat(DataScopeResolver.resolveType(List.of(scope(1L, DataScopeType.DEPT))))
                .isEqualTo(DataScopeType.DEPT);
    }

    @Test
    @DisplayName("没有任何角色时退回最严格的仅本人，而不是最宽松的全部")
    void noRolesFallsBackToStrictest() {
        assertThat(DataScopeResolver.resolveType(List.of())).isEqualTo(DataScopeType.SELF);
        assertThat(DataScopeResolver.resolveType(null)).isEqualTo(DataScopeType.SELF);
    }

    @Test
    @DisplayName("全部 优先于其余任何档位")
    void allBeatsEverything() {
        assertThat(DataScopeResolver.resolveType(List.of(
                scope(1L, DataScopeType.SELF),
                scope(2L, DataScopeType.ALL),
                scope(3L, DataScopeType.DEPT)
        ))).isEqualTo(DataScopeType.ALL);
    }

    @Test
    @DisplayName("本部门及以下 优先于自定义部门、本部门、仅本人")
    void deptAndChildBeatsCustomDeptSelf() {
        assertThat(DataScopeResolver.resolveType(List.of(
                scope(1L, DataScopeType.SELF),
                scope(2L, DataScopeType.DEPT),
                scope(3L, DataScopeType.CUSTOM),
                scope(4L, DataScopeType.DEPT_AND_CHILD)
        ))).isEqualTo(DataScopeType.DEPT_AND_CHILD);
    }

    @Test
    @DisplayName("自定义部门 优先于本部门、仅本人")
    void customBeatsDeptSelf() {
        assertThat(DataScopeResolver.resolveType(List.of(
                scope(1L, DataScopeType.SELF),
                scope(2L, DataScopeType.DEPT),
                scope(3L, DataScopeType.CUSTOM)
        ))).isEqualTo(DataScopeType.CUSTOM);
    }

    @Test
    @DisplayName("本部门 优先于仅本人")
    void deptBeatsSelf() {
        assertThat(DataScopeResolver.resolveType(List.of(
                scope(1L, DataScopeType.SELF),
                scope(2L, DataScopeType.DEPT)
        ))).isEqualTo(DataScopeType.DEPT);
    }

    @Test
    @DisplayName("data_scope 为 null 的角色被跳过，不参与合并、也不影响其余角色的结果")
    void nullDataScopeIsIgnored() {
        RoleScope broken = new RoleScope();
        broken.setRoleId(9L);
        broken.setDataScope(null);

        assertThat(DataScopeResolver.resolveType(List.of(broken, scope(1L, DataScopeType.DEPT))))
                .isEqualTo(DataScopeType.DEPT);
        assertThat(DataScopeResolver.resolveType(List.of(broken))).isEqualTo(DataScopeType.SELF);
    }

    private static RoleScope scope(Long roleId, DataScopeType type) {
        RoleScope roleScope = new RoleScope();
        roleScope.setRoleId(roleId);
        roleScope.setDataScope(type.getCode());
        return roleScope;
    }
}
