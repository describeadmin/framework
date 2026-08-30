package io.github.describeadmin.system.core;

import io.github.describeadmin.system.mapper.RoleScope;

import java.util.List;

/**
 * 把一个用户身兼多角色的默认首页合并为单一有效值。
 *
 * <p>合并规则：按角色 sort 升序（调用方 {@code SysRelationMapper.selectDataScopesByUserId}
 * 已保证 {@code roleScopes} 按此排序），取第一个设置了 {@code home_path} 的角色；
 * 全部未设置则返回 {@code null}，由调用方落回前端全局 {@code preferences.app.defaultHomePath}。
 *
 * <p>与 {@link DataScopeResolver} 不同，这里没有"最宽松/最严格"的语义，
 * 只有"谁排在前面谁生效"。
 *
 * <p>纯函数，不依赖 Spring，不查库。
 */
public final class HomePathResolver {

    private HomePathResolver() {
    }

    /**
     * @param roleScopes 用户全部角色，须按 sort 升序排列；{@code null}/空表示没有任何角色
     * @return 排序最靠前且非空的角色 home_path；没有任何角色设置过则返回 {@code null}
     */
    public static String resolve(List<RoleScope> roleScopes) {
        if (roleScopes == null) {
            return null;
        }
        for (RoleScope roleScope : roleScopes) {
            String homePath = roleScope.getHomePath();
            if (homePath != null && !homePath.isBlank()) {
                return homePath;
            }
        }
        return null;
    }
}
