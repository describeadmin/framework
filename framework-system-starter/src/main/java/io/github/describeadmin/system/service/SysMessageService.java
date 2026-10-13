package io.github.describeadmin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.PageQuery;
import io.github.describeadmin.common.api.PageResult;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.system.api.MessageDraft;
import io.github.describeadmin.system.api.MessageSender;
import io.github.describeadmin.system.entity.SysMessage;
import io.github.describeadmin.system.entity.SysUser;
import io.github.describeadmin.system.mapper.SysMessageMapper;
import io.github.describeadmin.system.mapper.SysUserMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 站内信。{@link MessageSender} 的框架默认实现，同时承担收件人自助的查询与操作。
 *
 * <p><b>不继承 {@code BaseService}</b>，理由同 {@link SysOperLogService}：{@link SysMessage}
 * 刻意不继承 {@code BaseEntity}。
 *
 * <p>自助方法（{@code *Own}）的 {@code userId} 一律由 Controller 取自登录态，
 * 每条 SQL 都带 {@code user_id} 条件，见 {@link SysMessageMapper}。
 */
@Service
public class SysMessageService implements MessageSender {

    /** 顶栏铃铛弹层展示的最近消息条数。 */
    public static final int INBOX_LATEST_SIZE = 10;

    private final SysMessageMapper mapper;
    private final SysUserMapper userMapper;
    private final Clock clock;

    public SysMessageService(SysMessageMapper mapper, SysUserMapper userMapper, ObjectProvider<Clock> clock) {
        this.mapper = mapper;
        this.userMapper = userMapper;
        this.clock = clock.getIfAvailable(Clock::systemDefaultZone);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void send(Collection<Long> userIds, MessageDraft draft) {
        Objects.requireNonNull(draft, "draft");
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Set<Long> recipients = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId != null) {
                recipients.add(userId);
            }
        }
        if (recipients.size() > MAX_RECIPIENTS) {
            throw new IllegalArgumentException(
                    "站内信单次收件人超过上限: " + recipients.size() + " > " + MAX_RECIPIENTS);
        }
        if (recipients.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<SysMessage> rows = new ArrayList<>(recipients.size());
        for (Long userId : recipients) {
            SysMessage row = new SysMessage();
            row.setUserId(userId);
            row.setSenderId(draft.senderId());
            row.setCategory(draft.category());
            row.setTitle(draft.title());
            row.setContent(draft.content());
            row.setLink(draft.link());
            row.setBizKey(draft.bizKey());
            row.setCreateTime(now);
            rows.add(row);
        }
        mapper.insert(rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markReadByBiz(String category, String bizKey) {
        if (category == null || category.isBlank() || bizKey == null || bizKey.isBlank()) {
            throw new IllegalArgumentException("markReadByBiz 的 category 与 bizKey 都不能为空");
        }
        return mapper.markReadByBiz(category, bizKey, LocalDateTime.now(clock));
    }

    /** 顶栏铃铛轮询用：未读数 + 最近 {@link #INBOX_LATEST_SIZE} 条（含已读）。 */
    public Inbox inboxOwn(Long userId) {
        long unread = mapper.selectCount(new QueryWrapper<SysMessage>()
                .eq("user_id", userId).isNull("read_time"));
        Page<SysMessage> page = mapper.selectPage(Page.of(1, INBOX_LATEST_SIZE, false),
                new QueryWrapper<SysMessage>().eq("user_id", userId).orderByDesc("id"));
        return new Inbox(Math.toIntExact(unread), fillSenders(page.getRecords()));
    }

    /** 消息中心分页。{@code unreadOnly} 为 true 时只看未读。 */
    public PageResult<SysMessage> pageOwn(Long userId, PageQuery query, String category, boolean unreadOnly) {
        QueryWrapper<SysMessage> wrapper = new QueryWrapper<SysMessage>()
                .eq("user_id", userId)
                .eq(category != null && !category.isBlank(), "category", category)
                .isNull(unreadOnly, "read_time")
                .orderByDesc("id");
        Page<SysMessage> result = mapper.selectPage(Page.of(query.getCurrent(), query.getSize()), wrapper);
        return new PageResult<>(fillSenders(result.getRecords()), result.getTotal(),
                result.getCurrent(), result.getSize());
    }

    /**
     * 标为已读。已经是已读的照样算成功（幂等）；不存在或不是自己的消息报"消息不存在"，
     * 不区分这两种情况，免得用 id 试探别人的消息是否存在。
     */
    public void readOwn(Long userId, Long id) {
        if (mapper.markRead(userId, id, LocalDateTime.now(clock)) == 0 && !existsOwn(userId, id)) {
            throw notFound(id);
        }
    }

    public int readAllOwn(Long userId) {
        return mapper.markAllRead(userId, LocalDateTime.now(clock));
    }

    public void removeOwn(Long userId, Long id) {
        if (mapper.deleteOwn(userId, id) == 0) {
            throw notFound(id);
        }
    }

    /** 清空已读，返回删除条数。未读消息保留。 */
    public int clearReadOwn(Long userId) {
        return mapper.deleteReadOwn(userId);
    }

    private boolean existsOwn(Long userId, Long id) {
        return mapper.selectCount(new QueryWrapper<SysMessage>().eq("id", id).eq("user_id", userId)) > 0;
    }

    private static BizException notFound(Long id) {
        return new BizException(ResultCode.NOT_FOUND, "消息不存在: " + id);
    }

    private List<SysMessage> fillSenders(List<SysMessage> rows) {
        Set<Long> senderIds = rows.stream()
                .map(SysMessage::getSenderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (senderIds.isEmpty()) {
            return rows;
        }
        Map<Long, SysUser> senders = userMapper.selectBriefByIdsIgnoreDataScope(senderIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
        for (SysMessage row : rows) {
            SysUser sender = row.getSenderId() == null ? null : senders.get(row.getSenderId());
            if (sender != null) {
                row.setSenderName(sender.getNickname());
                row.setSenderAvatar(sender.getAvatar());
            }
        }
        return rows;
    }

    /**
     * 铃铛轮询的响应体。
     *
     * @param unreadCount 用 {@code int} 而不是 {@code long}：框架把 {@code Long} 一律序列化成字符串
     *                    （CLAUDE.md 4.8），计数要保持数字形态
     */
    public record Inbox(int unreadCount, List<SysMessage> latest) {
    }
}
