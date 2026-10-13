package io.github.describeadmin.system.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MessageDraft} 的构造期校验。
 *
 * <p>link 是收件人会去点的东西，这里挡不住外链，就等于给钓鱼链接盖了"系统消息"的章。
 */
@DisplayName("MessageDraft 站内信草稿校验")
class MessageDraftTest {

    @ParameterizedTest
    @ValueSource(strings = {"/workorder/detail?id=123", "/system/user", "/a?x=1&y=%E4%B8%AD", "/"})
    @DisplayName("站内路径（可带 query）放行")
    void acceptsInternalPaths(String link) {
        assertThat(MessageDraft.of("workorder.approval", "收到待审批工单", null, link).link()).isEqualTo(link);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example.com", "//evil.example.com", "/\\evil.example.com",
            "javascript:alert(1)", "system/user", "/a b", "/a\\b", "/a\tb"})
    @DisplayName("外链、协议相对地址、反斜杠、空白一律拒绝")
    void rejectsExternalOrMalformedLinks(String link) {
        assertThatThrownBy(() -> MessageDraft.of("workorder.approval", "标题", null, link))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("link");
    }

    @Test
    @DisplayName("空串的 link / bizKey 规整为 null")
    void blankOptionalFieldsBecomeNull() {
        MessageDraft draft = new MessageDraft("c", "t", null, "", "", null);
        assertThat(draft.link()).isNull();
        assertThat(draft.bizKey()).isNull();
    }

    @Test
    @DisplayName("category、title 必填")
    void requiresCategoryAndTitle() {
        assertThatThrownBy(() -> MessageDraft.of(" ", "t", null, null)).hasMessageContaining("category");
        assertThatThrownBy(() -> MessageDraft.of("c", null, null, null)).hasMessageContaining("title");
    }

    @Test
    @DisplayName("超长抛错，不截断")
    void rejectsOverlongFields() {
        assertThatThrownBy(() -> MessageDraft.of("c".repeat(65), "t", null, null))
                .hasMessageContaining("category");
        assertThatThrownBy(() -> MessageDraft.of("c", "t".repeat(129), null, null))
                .hasMessageContaining("title");
        assertThatThrownBy(() -> MessageDraft.of("c", "t", "x".repeat(501), null))
                .hasMessageContaining("content");
        assertThatThrownBy(() -> MessageDraft.of("c", "t", null, "/" + "a".repeat(255)))
                .hasMessageContaining("link");
        assertThatThrownBy(() -> new MessageDraft("c", "t", null, null, "k".repeat(65), null))
                .hasMessageContaining("bizKey");
        assertThat(MessageDraft.of("c", "t", "x".repeat(500), null).content()).hasSize(500);
    }
}
