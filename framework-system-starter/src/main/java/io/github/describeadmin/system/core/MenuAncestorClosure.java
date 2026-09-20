package io.github.describeadmin.system.core;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把一组菜单 id 闭包为「自身 + 全部祖先」。
 *
 * <p>角色授权用：只授子节点而不授父节点是<b>无效授权</b>——前端按父子关系生成路由，
 * 父节点缺席时子路由没有挂载点（见 {@code apps/admin/src/api/core/auth.ts} 的
 * {@code toRouteRecords}）。闭包放在唯一写入口，库里存的就是事实，
 * 不必在每个读取口径上再补一次。
 *
 * <p><b>只用于菜单，不要套用到部门数据权限上。</b>菜单 id 是树上的挂载点，子依赖父
 * 才能被渲染；而 {@code sys_role_dept} 的部门 id 是一个行过滤值
 * （{@code DeptDataPermissionHandler} 的 CUSTOM 档生成 {@code dept_column IN (...)}，
 * 精确集合成员判定），给它补祖先等于让角色多看见父部门的业务数据。
 *
 * <p>纯函数，不依赖 Spring，不查库。
 */
public final class MenuAncestorClosure {

    private MenuAncestorClosure() {
    }

    /**
     * @param ids        要闭包的菜单 id；允许含重复值、{@code null} 元素、以及在
     *                   {@code parentById} 里不存在的 id
     * @param parentById 全量菜单的 {@code id -> parentId} 映射（调用方须已排除逻辑删除的记录）
     * @return 去重后的「自身 + 全部祖先」，顺序稳定；{@code ids} 为 {@code null}/空时返回空 List
     */
    public static List<Long> expand(Collection<Long> ids, Map<Long, Long> parentById) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> parents = parentById == null ? Map.of() : parentById;

        // 去重不是优化而是硬要求：sys_role_menu 的主键是 (role_id, menu_id)，
        // 入参同时含父与子时展开必然产生重复 id，insertRoleMenus 的批量 VALUES
        // 会直接抛 DuplicateKeyException。
        Set<Long> closure = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            walkUp(id, parents, closure);
        }
        return List.copyOf(closure);
    }

    /**
     * 从 {@code start} 向上收集到根，结果并入 {@code closure}。
     *
     * <p>{@code visited} 是 <b>per-walk</b> 的，不能提到方法外共享：兄弟节点共享祖先是
     * 完全合法的结构，用全局 visited 会在第二个兄弟那里提前截断，
     * 于是「先授 A 后授 B」和「先授 B 后授 A」得到不同的授权结果。
     */
    private static void walkUp(Long start, Map<Long, Long> parentById, Set<Long> closure) {
        Set<Long> visited = new HashSet<>();
        Long current = start;
        while (current != null && visited.add(current)) {
            // 不存在的 id 也原样保留：今天 assignMenus 就是原样插入的，这里不顺手改那个语义
            // ——否则「授权成功但少了几条」会变成一种新的静默行为。
            closure.add(current);
            Long parent = parentById.get(current);
            // parent_id = 0 是根（见 schema-rbac.sql 的 sys_menu.parent_id 注释）；
            // 查不到则说明父节点不存在或已被逻辑删除，两种情况都到此为止。
            current = parent == null || parent == 0L ? null : parent;
        }
        // visited.add 返回 false 即成环。菜单管理页可以把 parentId 改成任意值，
        // 没有任何约束阻止 A.parent=B, B.parent=A，所以这不是"不可能发生"。
        // 静默停止而不是抛异常：授权是管理员的日常动作，因为一条脏数据把它打成 500
        // 收益为负；脏数据本身由菜单管理页负责。
    }
}
