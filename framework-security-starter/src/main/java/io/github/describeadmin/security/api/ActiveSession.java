package io.github.describeadmin.security.api;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * 一个在线会话的只读快照。
 *
 * <p><b>刻意不包含令牌本身</b>：在线用户列表是给管理员看的，令牌一旦出现在这个响应里，
 * 任何能打开该页面的人都可以拿它冒充当事人。踢下线按用户维度进行
 * （{@link TokenStore#revokeAllOf}），不需要令牌，因此没有任何理由把它带出来。
 *
 * <p>一个用户可能同时持有多个会话（多设备、多浏览器），因此本类是<b>会话</b>粒度而非用户粒度，
 * 同一个 {@code userId} 可以出现多条。{@link #getIp()} / {@link #getDevice()} 正是用来
 * 区分这些会话"从哪来"的——它们的值在登录时由 Web 层写入（见 {@link SessionMeta}），
 * {@link TokenStore} 的实现若不记录来源信息，这两个字段为 {@code null}。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。
 */
public class ActiveSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final String nickname;

    /** 认证来源，取值为对应 {@link AuthProvider#type()}。 */
    private final String authType;

    private final Instant issuedAt;
    private final Instant expiresAt;

    /** 登录时的客户端 IP，可能为 {@code null}（实现未记录来源，或登录时拿不到请求上下文）。 */
    private final String ip;

    /** 登录时的设备描述，如 {@code "Chrome · Windows"}，可能为 {@code null}，理由同 {@link #ip}。 */
    private final String device;

    public ActiveSession(Long userId, String username, String nickname, String authType,
                         Instant issuedAt, Instant expiresAt, String ip, String device) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.authType = authType;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.ip = ip;
        this.device = device;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAuthType() {
        return authType;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getIp() {
        return ip;
    }

    public String getDevice() {
        return device;
    }
}
