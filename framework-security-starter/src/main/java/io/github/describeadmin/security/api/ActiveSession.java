package io.github.describeadmin.security.api;

import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

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
 * <p>{@link #getSessionId()} 是令牌的<b>单向摘要</b>（见 {@link #sessionIdOf(String)}），
 * 不是令牌本身，拿到它冒充不了任何人。它用来在列表里唯一标识一条会话，
 * 以及让服务端把"发起本次请求的那条会话"标出来（在线用户页的「当前」标记）。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。
 */
public class ActiveSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 会话标识，由 {@link #sessionIdOf(String)} 从令牌派生；实现未提供时为 {@code null}。 */
    private final String sessionId;

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

    /**
     * 不带会话标识的旧构造器，保留给已有的 {@link TokenStore} 实现。
     * 用它构造的会话 {@link #getSessionId()} 为 {@code null}，在线用户页不会把它标为「当前」。
     */
    public ActiveSession(Long userId, String username, String nickname, String authType,
                         Instant issuedAt, Instant expiresAt, String ip, String device) {
        this(null, userId, username, nickname, authType, issuedAt, expiresAt, ip, device);
    }

    /**
     * @param sessionId 用 {@link #sessionIdOf(String)} 从该会话的 access token 算出，<b>不要直接传令牌</b>
     */
    public ActiveSession(String sessionId, Long userId, String username, String nickname, String authType,
                         Instant issuedAt, Instant expiresAt, String ip, String device) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.authType = authType;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.ip = ip;
        this.device = device;
    }

    /**
     * 从 access token 派生会话标识：SHA-256 取前 128 位，十六进制。
     *
     * <p>{@link TokenStore} 的实现与需要识别"当前会话"的调用方必须用同一个算法，
     * 所以放在这里而不是让各实现自己挑。单向摘要保证响应里出现它不等于泄露令牌；
     * 截断到 128 位只是为了短，碰撞概率对在线会话这个量级可以忽略。
     *
     * @return 令牌为 {@code null} 时返回 {@code null}
     */
    public static String sessionIdOf(String token) {
        if (token == null) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 Java 平台规范要求每个实现都必须提供的算法，走不到这里
            throw new IllegalStateException(e);
        }
    }

    public String getSessionId() {
        return sessionId;
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
