package io.github.describeadmin.system.core;

import io.github.describeadmin.system.mapper.RoleScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HomePathResolver} 多角色默认首页合并的单元测试。
 *
 * <p>纯函数，不起 Spring 上下文。合并规则是"按传入顺序取第一个非空值"，
 * 调用方（{@code SysRelationMapper.selectDataScopesByUserId}）负责按角色 sort 排序。
 */
@DisplayName("HomePathResolver 多角色首页合并")
class HomePathResolverTest {

    @Test
    @DisplayName("没有任何角色时返回 null，由前端落回全局 defaultHomePath")
    void noRolesReturnsNull() {
        assertThat(HomePathResolver.resolve(List.of())).isNull();
        assertThat(HomePathResolver.resolve(null)).isNull();
    }

    @Test
    @DisplayName("单角色设置了首页则直接生效")
    void singleRoleWithHomePath() {
        assertThat(HomePathResolver.resolve(List.of(scope(1L, "/system/dict"))))
                .isEqualTo("/system/dict");
    }

    @Test
    @DisplayName("全部角色都未设置首页时返回 null")
    void allRolesWithoutHomePath() {
        assertThat(HomePathResolver.resolve(List.of(scope(1L, null), scope(2L, "")))).isNull();
    }

    @Test
    @DisplayName("按传入顺序取第一个非空值，排在前面的角色优先")
    void firstNonBlankWins() {
        assertThat(HomePathResolver.resolve(List.of(
                scope(1L, null),
                scope(2L, "/system/dict"),
                scope(3L, "/system/user")
        ))).isEqualTo("/system/dict");
    }

    @Test
    @DisplayName("空白字符串等同于未设置，跳过继续找下一个")
    void blankHomePathIsSkipped() {
        assertThat(HomePathResolver.resolve(List.of(
                scope(1L, "   "),
                scope(2L, "/system/user")
        ))).isEqualTo("/system/user");
    }

    private static RoleScope scope(Long roleId, String homePath) {
        RoleScope roleScope = new RoleScope();
        roleScope.setRoleId(roleId);
        roleScope.setHomePath(homePath);
        return roleScope;
    }
}
