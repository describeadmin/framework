package io.github.describeadmin.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TreeBuilder} 的建树行为。
 *
 * <p>随 0.2.0 从 framework-system-starter 上提时补齐——它此前一直没有测试，
 * 而"孤儿节点提升为根"是一条刻意设计、很容易在重构中被当成 bug 改掉的行为。
 */
@DisplayName("TreeBuilder 建树")
class TreeBuilderTest {

    @Test
    @DisplayName("按父子关系组装，保持输入顺序")
    void buildsTree() {
        List<Node> flat = List.of(
                new Node(1L, null), new Node(2L, 1L), new Node(3L, 1L), new Node(4L, 2L));

        List<Node> roots = build(flat);

        assertThat(roots).extracting(Node::getId).containsExactly(1L);
        assertThat(roots.get(0).getChildren()).extracting(Node::getId).containsExactly(2L, 3L);
        assertThat(roots.get(0).getChildren().get(0).getChildren())
                .extracting(Node::getId).containsExactly(4L);
    }

    @Test
    @DisplayName("parentId 为 0 与为 null 都算根——两种表示历史上都出现过")
    void treatsZeroAndNullAsRoot() {
        List<Node> roots = build(List.of(new Node(1L, null), new Node(2L, 0L)));

        assertThat(roots).extracting(Node::getId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("孤儿节点提升为根，而不是被静默丢弃")
    void promotesOrphansToRoot() {
        // 999 不存在：父节点被逻辑删除，或数据本身有问题
        List<Node> roots = build(List.of(new Node(1L, null), new Node(2L, 999L)));

        assertThat(roots).extracting(Node::getId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("空列表返回空结果，不抛异常")
    void handlesEmptyInput() {
        assertThat(build(List.of())).isEmpty();
    }

    private static List<Node> build(List<Node> flat) {
        return TreeBuilder.build(flat, Node::getId, Node::getParentId, Node::getChildren);
    }

    static class Node {
        private final Long id;
        private final Long parentId;
        private final List<Node> children = new ArrayList<>();

        Node(Long id, Long parentId) {
            this.id = id;
            this.parentId = parentId;
        }

        Long getId() {
            return id;
        }

        Long getParentId() {
            return parentId;
        }

        List<Node> getChildren() {
            return children;
        }
    }
}
