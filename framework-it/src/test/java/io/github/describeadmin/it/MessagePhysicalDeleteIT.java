package io.github.describeadmin.it;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.system.api.MessageDraft;
import io.github.describeadmin.system.entity.SysMessage;
import io.github.describeadmin.system.service.SysMessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "describeadmin.system.message.delete-mode=physical")
@DisplayName("站内信物理删除")
class MessagePhysicalDeleteIT extends AbstractMySqlIntegrationTest {

    @Autowired SysMessageService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("单条删除和清空已读只物理删除当前收件人的有效消息")
    void deletesOnlyActiveOwnMessages() {
        long userId = 9_100_001L;
        long otherUserId = 9_100_002L;
        service.send(List.of(userId, otherUserId), MessageDraft.of("system.notice", "清空", null, null));
        service.send(List.of(userId), MessageDraft.of("system.notice", "单条删除", null, null));
        service.send(List.of(userId), MessageDraft.of("system.notice", "历史软删除", null, null));
        service.send(List.of(userId), MessageDraft.of("system.notice", "保留未读", null, null));

        Long clearedId = messageId(userId, "清空");
        Long otherId = messageId(otherUserId, "清空");
        Long removedId = messageId(userId, "单条删除");
        Long softDeletedId = messageId(userId, "历史软删除");
        service.readOwn(userId, clearedId);
        service.readOwn(otherUserId, otherId);
        service.readOwn(userId, softDeletedId);
        jdbc.update("UPDATE sys_message SET deleted = 1 WHERE id = ?", softDeletedId);

        assertThatThrownBy(() -> service.removeOwn(userId, otherId))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()));
        assertThatThrownBy(() -> service.removeOwn(userId, softDeletedId))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()));
        service.removeOwn(userId, removedId);
        assertThat(service.clearReadOwn(userId)).isEqualTo(1);
        assertThat(service.clearReadOwn(userId)).isZero();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_message WHERE id IN (?, ?)",
                Integer.class, removedId, clearedId)).isZero();
        assertThat(jdbc.queryForObject("SELECT deleted FROM sys_message WHERE id = ?",
                Integer.class, softDeletedId)).isEqualTo(1);
        assertThat(service.inboxOwn(userId).latest()).extracting(SysMessage::getTitle).containsExactly("保留未读");
        assertThat(service.inboxOwn(otherUserId).latest()).extracting(SysMessage::getId).containsExactly(otherId);
    }

    private Long messageId(long userId, String title) {
        return jdbc.queryForObject("SELECT id FROM sys_message WHERE user_id = ? AND title = ?",
                Long.class, userId, title);
    }
}
