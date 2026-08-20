package io.github.describeadmin.security.core;

import io.github.describeadmin.security.api.ActiveSession;
import io.github.describeadmin.security.api.LoginUser;
import io.github.describeadmin.security.api.TokenStore;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link TokenStore} 的内存实现，框架默认值。
 *
 * <p><b>适用范围与已知局限（部署前必读）</b>：
 * <ul>
 *   <li>令牌存活在进程内存中，<b>应用重启后全部失效</b>，用户需要重新登录</li>
 *   <li><b>不支持多实例部署</b>——实例 A 签发的令牌在实例 B 上解析不出来</li>
 * </ul>
 * 本项目面向中小型、基本不涉及分布式的场景，单机部署下这两条都可接受。
 * 一旦要多实例或要求重启不掉线，换成 Redis 实现即可，上层代码不用动。
 *
 * <p>过期令牌采用<b>惰性清理 + 写入时按量清理</b>，不起后台线程：
 * 起线程就要管生命周期、要考虑与业务方线程池的关系，对这个数据量不划算。
 */
public class InMemoryTokenStore implements TokenStore {

    /** 触发一次全量清理的签发次数间隔。取值不敏感，只是避免每次签发都全表扫。 */
    private static final int SWEEP_INTERVAL = 256;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();
    private final AtomicLong issueCount = new AtomicLong();
    private final Duration ttl;

    public InMemoryTokenStore(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("令牌有效期必须为正数，当前为: " + ttl);
        }
        this.ttl = ttl;
    }

    @Override
    public String issue(LoginUser user) {
        if (user == null) {
            throw new IllegalArgumentException("不能为 null 用户签发令牌");
        }
        // 256 位随机量。令牌是不透明的，本身不携带任何信息，猜中概率可忽略
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = ENCODER.encodeToString(raw);

        Instant now = Instant.now();
        tokens.put(token, new Entry(user, now, now.plus(ttl)));
        if (issueCount.incrementAndGet() % SWEEP_INTERVAL == 0) {
            sweepExpired();
        }
        return token;
    }

    @Override
    public Optional<LoginUser> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Entry entry = tokens.get(token);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            // 惰性清理：解析到过期令牌时顺手删掉
            tokens.remove(token, entry);
            return Optional.empty();
        }
        return Optional.of(entry.user());
    }

    @Override
    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            tokens.remove(token);
        }
    }

    @Override
    public int revokeAllOf(Long userId) {
        if (userId == null) {
            return 0;
        }
        int[] removed = {0};
        tokens.entrySet().removeIf(e -> {
            if (userId.equals(e.getValue().user().getUserId())) {
                removed[0]++;
                return true;
            }
            return false;
        });
        return removed[0];
    }

    @Override
    public List<ActiveSession> listActive() {
        Instant now = Instant.now();
        List<ActiveSession> sessions = new ArrayList<>();
        for (Entry entry : tokens.values()) {
            // 不返回已过期但尚未被清理掉的条目——惰性清理意味着它们可能还在 map 里，
            // 但它们已经不能用于认证，列进"在线用户"会让管理员看到并不存在的会话
            if (entry.expiresAt().isBefore(now)) {
                continue;
            }
            LoginUser user = entry.user();
            sessions.add(new ActiveSession(user.getUserId(), user.getUsername(),
                    user.getNickname(), user.getAuthType(),
                    entry.issuedAt(), entry.expiresAt()));
        }
        // 最近登录的排在前面，这是管理页面唯一有意义的默认顺序
        sessions.sort(Comparator.comparing(ActiveSession::getIssuedAt).reversed());
        return sessions;
    }

    /** 当前有效令牌数，供测试与监控使用。 */
    public int size() {
        sweepExpired();
        return tokens.size();
    }

    private void sweepExpired() {
        Instant now = Instant.now();
        tokens.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    private record Entry(LoginUser user, Instant issuedAt, Instant expiresAt) {
    }
}
