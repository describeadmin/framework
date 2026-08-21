package io.github.describeadmin.system.core;

import io.github.describeadmin.common.api.DataScopeType;
import io.github.describeadmin.system.mapper.RoleScope;

import java.util.List;

/**
 * 把一个用户身兼多角色的数据权限范围合并为单一有效范围。
 *
 * <p>合并规则：取全部角色里<b>最宽松的一档</b>生效——全部 &gt; 本部门及以下 &gt; 自定义部门
 * &gt; 本部门 &gt; 仅本人。这是刻意选的简单规则：多角色条件用 OR 拼接语义上更精确，
 * 但"本部门及以下"与"自定义部门"混用时条件会变复杂，V1 用这条简单规则换低风险，
 * 多角色取并集留作后续增强（见 CHANGELOG）。
 *
 * <p><b>宽松程度不是 {@link DataScopeType} 的声明顺序</b>，是本类维护的独立排序——
 * 两者故意分开，改一下枚举声明顺序不该悄悄改变合并策略。
 *
 * <p>纯函数，不依赖 Spring，不查库。
 */
public final class DataScopeResolver {

    /** 从最宽松到最严格。 */
    private static final List<DataScopeType> PERMISSIVENESS_ORDER = List.of(
            DataScopeType.ALL,
            DataScopeType.DEPT_AND_CHILD,
            DataScopeType.CUSTOM,
            DataScopeType.DEPT,
            DataScopeType.SELF);

    private DataScopeResolver() {
    }

    /**
     * @param roleScopes 用户全部角色的数据权限范围；{@code null}/空表示没有任何角色
     * @return 合并后的有效范围；无角色时返回最严格的 {@link DataScopeType#SELF}，
     *         与本框架"默认拒绝"的既有取向一致（见 CLAUDE.md 4.5 权限点默认拒绝的说明）
     */
    public static DataScopeType resolveType(List<RoleScope> roleScopes) {
        DataScopeType best = DataScopeType.SELF;
        if (roleScopes == null) {
            return best;
        }
        int bestRank = PERMISSIVENESS_ORDER.indexOf(best);
        for (RoleScope roleScope : roleScopes) {
            if (roleScope.getDataScope() == null) {
                continue;
            }
            DataScopeType type = DataScopeType.ofCode(roleScope.getDataScope());
            int rank = PERMISSIVENESS_ORDER.indexOf(type);
            if (rank < bestRank) {
                best = type;
                bestRank = rank;
            }
        }
        return best;
    }
}
