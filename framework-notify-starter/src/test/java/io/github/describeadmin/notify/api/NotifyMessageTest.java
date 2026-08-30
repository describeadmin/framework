package io.github.describeadmin.notify.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link NotifyMessage} 的单元测试。
 */
@DisplayName("通知消息")
class NotifyMessageTest {

    @Test
    @DisplayName("null receivers 归一化为空列表")
    void nullReceiversBecomeEmptyList() {
        NotifyMessage message = new NotifyMessage("标题", "正文", null);

        assertThat(message.receivers()).isEmpty();
    }

    @Test
    @DisplayName("receivers 列表不可变")
    void receiversListIsUnmodifiable() {
        NotifyMessage message = new NotifyMessage("标题", "正文", new ArrayList<>(List.of("u1")));

        assertThatThrownBy(() -> message.receivers().add("u2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("空白 content 被拒绝")
    void blankContentRejected() {
        assertThatThrownBy(() -> new NotifyMessage("标题", "   ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotifyMessage("标题", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
