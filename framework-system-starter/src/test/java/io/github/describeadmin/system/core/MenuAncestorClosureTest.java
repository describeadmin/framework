package io.github.describeadmin.system.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MenuAncestorClosure} 菜单祖先闭包的单元测试。
 *
 * <p>纯函数，不起 Spring 上下文、不查库。映射用 {@code id -> parentId}，
 * {@code parentId = 0} 表示根（与 {@code sys_menu.parent_id} 的约定一致）。
 *
 * <p>典型形状按框架约定是三层：DIR（目录，parent=0）→ MENU（列表页）→ BUTTON（按钮）。
 */
@DisplayName("MenuAncestorClosure 菜单祖先闭包")
class MenuAncestorClosureTest {

    /** 系统管理(1) → 用户管理(2) → 新增(3)/编辑(4)；另有一条平行的角色管理(5) → 新增(6)。 */
    private static final Map<Long, Long> TREE = tree(
            1L, 0L,
            2L, 1L,
            3L, 2L,
            4L, 2L,
            5L, 1L,
            6L, 5L
    );

    @Test
    @DisplayName("只授最深的 BUTTON，读回自身 + MENU + DIR 三条")
    void expandsDeepestButtonToFullChain() {
        assertThat(MenuAncestorClosure.expand(List.of(3L), TREE))
                .containsExactlyInAnyOrder(3L, 2L, 1L);
    }

    @Test
    @DisplayName("同时传父与子不产生重复 —— sys_role_menu 的主键是 (role_id, menu_id)")
    void parentAndChildTogetherProduceNoDuplicate() {
        List<Long> result = MenuAncestorClosure.expand(List.of(3L, 2L, 1L), TREE);
        assertThat(result).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(result).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("两个兄弟共享祖先时，祖先只出现一次")
    void sharedAncestorAppearsOnce() {
        List<Long> result = MenuAncestorClosure.expand(List.of(3L, 4L), TREE);
        assertThat(result).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
        assertThat(result).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("两棵子树各自向上闭包，互不干扰")
    void twoSubtreesExpandIndependently() {
        assertThat(MenuAncestorClosure.expand(List.of(3L, 6L), TREE))
                .containsExactlyInAnyOrder(1L, 2L, 3L, 5L, 6L);
    }

    @Test
    @DisplayName("先授 A 后授 B 与反过来结果相同 —— visited 是 per-walk 的，不会截断共享祖先")
    void orderDoesNotAffectResult() {
        assertThat(MenuAncestorClosure.expand(List.of(3L, 4L), TREE))
                .containsExactlyInAnyOrderElementsOf(MenuAncestorClosure.expand(List.of(4L, 3L), TREE));
    }

    @Test
    @DisplayName("传根节点时闭包是空操作，原样返回一条")
    void rootExpandsToItself() {
        assertThat(MenuAncestorClosure.expand(List.of(1L), TREE)).containsExactly(1L);
    }

    @Test
    @DisplayName("空入参 / null 入参返回空 List，不抛异常")
    void emptyInputReturnsEmpty() {
        assertThat(MenuAncestorClosure.expand(List.of(), TREE)).isEmpty();
        assertThat(MenuAncestorClosure.expand(null, TREE)).isEmpty();
    }

    @Test
    @DisplayName("入参里的 null 元素被跳过，其余照常闭包")
    void nullElementIsSkipped() {
        assertThat(MenuAncestorClosure.expand(Arrays.asList(null, 3L), TREE))
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("不存在于映射里的 id 原样保留 —— 不把它静默丢掉")
    void unknownIdIsKeptAsIs() {
        assertThat(MenuAncestorClosure.expand(List.of(999L), TREE)).containsExactly(999L);
    }

    @Test
    @DisplayName("parentId 指向不存在的菜单（父被逻辑删除）时走到那里停止，不抛异常")
    void danglingParentStopsTheWalk() {
        assertThat(MenuAncestorClosure.expand(List.of(7L), tree(7L, 404L)))
                .containsExactlyInAnyOrder(7L, 404L);
    }

    @Test
    @DisplayName("parentId 为 null 时当根处理")
    void nullParentIsTreatedAsRoot() {
        Map<Long, Long> withNullParent = new LinkedHashMap<>();
        withNullParent.put(8L, null);
        assertThat(MenuAncestorClosure.expand(List.of(8L), withNullParent)).containsExactly(8L);
    }

    @Test
    @DisplayName("parentById 为 null 时不抛，入参原样返回")
    void nullMappingDoesNotThrow() {
        assertThat(MenuAncestorClosure.expand(List.of(3L), null)).containsExactly(3L);
    }

    // 成环的两条用例：菜单管理页可以把 parentId 改成任意值，没有任何约束阻止
    // A.parent=B, B.parent=A。没有 per-walk visited 的实现会在这里死循环，
    // 而死循环表现为"点确定之后一直转圈"，不指向"菜单父子关系成环"。
    //
    // ⚠️ threadMode = SEPARATE_THREAD 不能省，写成 @Timeout(5) 这道防护就是假的。
    // JUnit 5 的 @Timeout 默认是 SAME_THREAD，它在测试方法【返回之后】才比对耗时——
    // 打不断死循环，于是用例既不通过也不失败，整个 surefire fork 永远挂在那里，
    // CI 表现为构建超时而不是测试失败，日志里没有任何东西指向这两条用例。
    // SEPARATE_THREAD 让测试跑在另一个线程上，主线程到点即判失败。
    // （实测过：去掉 walkUp 里的 visited 守卫后，SAME_THREAD 版本跑满 600 秒未退出。）

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("A ↔ B 互为父子时收集两条后停止，不死循环")
    void mutualCycleTerminates() {
        assertThat(MenuAncestorClosure.expand(List.of(10L), tree(10L, 11L, 11L, 10L)))
                .containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @DisplayName("自己是自己的父节点时只收集一条，不死循环")
    void selfCycleTerminates() {
        assertThat(MenuAncestorClosure.expand(List.of(12L), tree(12L, 12L))).containsExactly(12L);
    }

    /** {@code id, parentId, id, parentId, ...} 成对构造映射。 */
    private static Map<Long, Long> tree(Long... idThenParent) {
        Map<Long, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < idThenParent.length; i += 2) {
            map.put(idThenParent[i], idThenParent[i + 1]);
        }
        return map;
    }
}
