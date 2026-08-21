package io.github.describeadmin.notify.core;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.describeadmin.notify.api.NotifyMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LogNotifyChannel} 的单元测试。
 *
 * <p>断言日志的具体内容而不是"有没有打印一行日志"——对应 CLAUDE.md 3.6
 * "断言要比对具体值，不要只比对行数"，这里换成了日志场景：字符集/格式化坏掉时，
 * "日志行存在"这条断言照样通过，掩盖不了问题。
 */
@DisplayName("日志通知渠道")
class LogNotifyChannelTest {

    private final LogNotifyChannel channel = new LogNotifyChannel();
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(LogNotifyChannel.class);
        appender = new ListAppender<>();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("channel 返回 log")
    void channelReturnsLog() {
        assertThat(channel.channel()).isEqualTo("log");
    }

    @Test
    @DisplayName("send 记录标题、正文与收件人的具体内容")
    void sendLogsTitleContentAndReceivers() {
        channel.send(new NotifyMessage("超级管理员通知", "您的账号存在异常登录", List.of("u1", "u2")));

        assertThat(appender.list).hasSize(1);
        String formatted = appender.list.get(0).getFormattedMessage();
        assertThat(formatted).contains("超级管理员通知", "您的账号存在异常登录", "u1", "u2");
    }
}
