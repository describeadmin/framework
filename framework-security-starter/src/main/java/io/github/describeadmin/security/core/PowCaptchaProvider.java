package io.github.describeadmin.security.core;

import io.github.describeadmin.cache.api.CacheProvider;
import io.github.describeadmin.security.api.CaptchaChallenge;
import io.github.describeadmin.security.api.CaptchaProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 工作量证明（Proof of Work）验证码，核心内置的 {@link CaptchaProvider} 默认实现。
 *
 * <p>服务端随机取一个秘密数 {@code n ∈ [0, maxNumber]}，下发 {@code salt} 与
 * {@code challenge = hex(SHA-256(salt + n))}；浏览器从 0 起逐个尝试，找到哈希相等的那个数作为答案提交。
 * 用户不需要输入任何东西，浏览器后台算完即可；服务端出题与校验各只算一次哈希。
 *
 * <p><b>定位</b>：它不判断"是不是人"，只让每次尝试付出算力成本、挡掉不会解题的朴素脚本。
 * 浏览器可接受的难度对原生代码 / GPU 来说仍然很快，所以这是减速带而不是墙——
 * 每个账号的尝试次数真正封顶靠 {@link LoginAttemptGuard}。相比图形验证码
 * （{@link ImageCaptchaProvider}，多模态模型 / OCR 几乎零成本识别），防护不弱、体验更好。
 *
 * <p><b>有状态而非签名</b>：题目存进 {@link CacheProvider}，{@link #verify} 读取后立即 evict，
 * 天然满足"一次性"语义；也不需要多实例共享 HMAC 密钥——换成集中式缓存即天然多实例。
 *
 * <p><b>可测试性设计</b>：{@link #CACHE_KEY_PREFIX} 刻意声明为 {@code public}，理由同
 * {@link ImageCaptchaProvider}。不过本实现的答案可以直接暴力求解（默认难度几毫秒），
 * 集成测试更推荐走真实解题路径。
 */
public class PowCaptchaProvider implements CaptchaProvider {

    public static final String TYPE = "pow";

    /** 缓存键前缀，public 是刻意的——见类注释"可测试性设计"。 */
    public static final String CACHE_KEY_PREFIX = "describeadmin:captcha:pow:";

    /** 下发给前端的哈希算法名，前端据此选择实现；将来换算法时前后端按这个字段协商。 */
    public static final String ALGORITHM = "SHA-256";

    private static final int SALT_BYTES = 16;
    private static final HexFormat HEX = HexFormat.of();

    private final CacheProvider cache;
    private final Duration ttl;
    private final int maxNumber;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param maxNumber 秘密数的上界（含），即难度——浏览器平均要尝试 {@code maxNumber / 2} 次。
     *                  刻意用 {@code int}：{@code Long} 会按 CLAUDE.md 4.8 序列化成字符串，
     *                  而浏览器可接受的难度离 int 上限差好几个数量级
     */
    public PowCaptchaProvider(CacheProvider cache, Duration ttl, int maxNumber) {
        if (cache == null) {
            throw new IllegalArgumentException("CacheProvider 不能为空");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("验证码有效期必须为正数，当前为: " + ttl);
        }
        if (maxNumber <= 0) {
            throw new IllegalArgumentException("PoW 难度上界必须为正数，当前为: " + maxNumber);
        }
        this.cache = cache;
        this.ttl = ttl;
        this.maxNumber = maxNumber;
    }

    @Override
    public CaptchaChallenge generate() {
        byte[] saltBytes = new byte[SALT_BYTES];
        random.nextBytes(saltBytes);
        String salt = HEX.formatHex(saltBytes);
        long secret = random.nextLong((long) maxNumber + 1);
        String challenge = hash(salt, secret);

        String captchaId = UUID.randomUUID().toString();
        // salt 是 32 位十六进制、不含冒号，按第一个冒号切分是安全的
        cache.put(CACHE_KEY_PREFIX + captchaId, salt + ":" + challenge, ttl);
        return new CaptchaChallenge(captchaId, TYPE, Map.of(
                "algorithm", ALGORITHM,
                "salt", salt,
                "challenge", challenge,
                "maxNumber", maxNumber));
    }

    @Override
    public boolean verify(String captchaId, String answer) {
        if (captchaId == null || captchaId.isBlank()) {
            return false;
        }
        String key = CACHE_KEY_PREFIX + captchaId;
        Optional<String> stored = cache.get(key, String.class);
        // 无论对错都失效——一次性语义，防止同一道题被反复重放
        cache.evict(key);
        if (stored.isEmpty()) {
            return false;
        }
        long number;
        try {
            number = Long.parseLong(answer == null ? "" : answer.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (number < 0 || number > maxNumber) {
            return false;
        }
        String value = stored.get();
        int sep = value.indexOf(':');
        if (sep < 0) {
            return false;
        }
        String salt = value.substring(0, sep);
        String expected = value.substring(sep + 1);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                hash(salt, number).getBytes(StandardCharsets.US_ASCII));
    }

    /** {@code hex(SHA-256(salt + number))}，number 按十进制字符串拼接。前端求解器必须与此逐字节一致。 */
    static String hash(String salt, long number) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            return HEX.formatHex(digest.digest((salt + number).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 Java SE 规范要求每个实现都必须提供的算法，走不到这里
            throw new IllegalStateException(e);
        }
    }
}
