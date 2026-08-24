package io.github.describeadmin.common.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 通用树构建工具：把扁平列表按父子关系组装成树。
 *
 * <p><b>为什么在内存里建树、而不是用递归查询</b>：
 * MySQL 8.0 的递归 CTE（{@code WITH RECURSIVE}）是最直观的做法，但 CLAUDE.md 3.1 的
 * SQL 红线明确禁用 CTE —— 它在 MySQL 5.7 上不存在，在部分国产化数据库上也不可用。
 * 菜单与部门这类数据量通常在百到千级，一次全量查询 + 内存组装的开销可以忽略，
 * 用它换取跨数据库的确定性是划算的。
 *
 * <p>数据量真正大到内存建树不可接受时，应改用物化路径（{@code ancestors} 字段）方案，
 * 那同样只需要基础 SQL，不必引入 CTE。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。0.2.0 之前它在
 * {@code framework-system-starter} 的 {@code core} 包下，业务方建部门/字典/分类树时
 * 拿不到；上提到本模块后，只用 Web 层、不用系统管理模块的业务方也能复用。
 */
public final class TreeBuilder {

    private TreeBuilder() {
    }

    /**
     * 组装为树。
     *
     * <p>孤儿节点（父节点不存在或已被逻辑删除）会被提升为根节点，而不是被静默丢弃——
     * 数据本身有问题时，让它可见比让它消失更容易排查。
     *
     * @param flat            扁平列表，调用方需自行保证排序
     * @param idGetter        取节点 ID
     * @param parentIdGetter  取父节点 ID；根节点应为 {@code null} 或 {@code 0}
     * @param childrenGetter  取子节点容器（需已初始化为可变 List）
     */
    public static <T> List<T> build(List<T> flat,
                                    Function<T, Long> idGetter,
                                    Function<T, Long> parentIdGetter,
                                    Function<T, List<T>> childrenGetter) {
        Map<Long, T> byId = new LinkedHashMap<>();
        for (T node : flat) {
            byId.put(idGetter.apply(node), node);
        }

        List<T> roots = new ArrayList<>();
        for (T node : flat) {
            Long parentId = parentIdGetter.apply(node);
            if (parentId == null || parentId == 0L) {
                roots.add(node);
                continue;
            }
            T parent = byId.get(parentId);
            if (parent == null) {
                // 孤儿节点：父节点不存在或已被逻辑删除，提升为根以免数据"凭空消失"
                roots.add(node);
            } else {
                childrenGetter.apply(parent).add(node);
            }
        }
        return roots;
    }
}
