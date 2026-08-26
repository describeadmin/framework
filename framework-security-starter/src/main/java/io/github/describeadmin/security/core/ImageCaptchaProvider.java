package io.github.describeadmin.security.core;

import io.github.describeadmin.cache.api.CacheProvider;
import io.github.describeadmin.security.api.CaptchaChallenge;
import io.github.describeadmin.security.api.CaptchaProvider;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;

/**
 * 图形字符验证码，核心内置的 {@link CaptchaProvider} 默认实现。
 *
 * <p>用 JDK 自带的 {@link BufferedImage}/{@link Graphics2D} 离屏渲染，零外部依赖
 * （不引入 Hutool/EasyCaptcha 等第三方验证码库，见 CLAUDE.md 4.7）。正确答案存入
 * {@link CacheProvider}（键前缀 {@link #CACHE_KEY_PREFIX}），{@link #verify} 读取后
 * 立即 {@link CacheProvider#evict evict}，因此天然满足 {@link CaptchaProvider#verify}
 * 要求的"一次性"语义。
 *
 * <p><b>可测试性设计</b>：{@link #CACHE_KEY_PREFIX} 刻意声明为 {@code public}——图形验证码
 * 是给人眼识别的，自动化测试无法"读图作答"，只能绕过去：集成测试通过注入的
 * {@link CacheProvider} 直接用 {@code CACHE_KEY_PREFIX + captchaId} 读出正确答案。
 * 这不是走后门，是这个类刻意为可测试性做出的设计。
 *
 * <p><b>部署环境提示</b>：本实现依赖 {@code java.desktop} 模块（{@link BufferedImage}/
 * {@link Graphics2D}/{@link ImageIO} 所在模块）。标准 JDK/JRE 发行版都包含它；如果业务方
 * 用 {@code jlink} 自定义精简运行时，需要确认没有裁掉 {@code java.desktop}，否则启动期
 * 不会报错，调用 {@link #generate()} 时才会抛 {@code NoClassDefFoundError}。
 */
public class ImageCaptchaProvider implements CaptchaProvider {

    public static final String TYPE = "image";

    /** 缓存键前缀，public 是刻意的——见类注释"可测试性设计"。 */
    public static final String CACHE_KEY_PREFIX = "describeadmin:captcha:";

    /** 候选字符集：排除 0/O/1/I/L 等易混淆字符。 */
    private static final char[] CANDIDATE_CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();

    private static final int IMAGE_WIDTH = 120;
    private static final int IMAGE_HEIGHT = 40;

    private final CacheProvider cache;
    private final Duration ttl;
    private final int codeLength;

    public ImageCaptchaProvider(CacheProvider cache, Duration ttl, int codeLength) {
        if (cache == null) {
            throw new IllegalArgumentException("CacheProvider 不能为空");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("验证码有效期必须为正数，当前为: " + ttl);
        }
        if (codeLength <= 0) {
            throw new IllegalArgumentException("验证码长度必须为正数，当前为: " + codeLength);
        }
        this.cache = cache;
        this.ttl = ttl;
        this.codeLength = codeLength;
    }

    @Override
    public CaptchaChallenge generate() {
        String code = randomCode();
        String captchaId = UUID.randomUUID().toString();
        cache.put(CACHE_KEY_PREFIX + captchaId, code, ttl);
        String image = renderToDataUri(code);
        return new CaptchaChallenge(captchaId, TYPE, Map.of("image", image));
    }

    @Override
    public boolean verify(String captchaId, String answer) {
        if (captchaId == null || captchaId.isBlank()) {
            return false;
        }
        String key = CACHE_KEY_PREFIX + captchaId;
        Optional<String> expected = cache.get(key, String.class);
        // 无论对错都失效——一次性语义，防止同一张验证码被反复重放
        cache.evict(key);
        return expected.isPresent() && expected.get().equalsIgnoreCase(answer);
    }

    private String randomCode() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            sb.append(CANDIDATE_CHARS[random.nextInt(CANDIDATE_CHARS.length)]);
        }
        return sb.toString();
    }

    /**
     * 离屏渲染成扭曲字符图片，编码为 {@code data:image/png;base64,...}。
     *
     * <p>纯离屏渲染（{@code BufferedImage} + {@code Graphics2D}），不触碰任何
     * {@code Toolkit}/{@code Display}，headless 环境下天然可用。
     */
    private String renderToDataUri(String code) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(240, 240, 240));
            g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

            // 干扰线
            for (int i = 0; i < 6; i++) {
                g.setColor(randomColor(random, 150, 220));
                g.drawLine(random.nextInt(IMAGE_WIDTH), random.nextInt(IMAGE_HEIGHT),
                        random.nextInt(IMAGE_WIDTH), random.nextInt(IMAGE_HEIGHT));
            }

            // 每个字符独立旋转/着色
            int charWidth = IMAGE_WIDTH / code.length();
            for (int i = 0; i < code.length(); i++) {
                g.setFont(new Font("Serif", Font.BOLD, 24));
                g.setColor(randomColor(random, 20, 130));
                AffineTransform original = g.getTransform();
                double angle = Math.toRadians(random.nextInt(46) - 23); // ±23°
                int x = charWidth * i + 8;
                int y = IMAGE_HEIGHT / 2 + 9;
                g.rotate(angle, x, y);
                g.drawString(String.valueOf(code.charAt(i)), x, y);
                g.setTransform(original);
            }

            // 噪点
            for (int i = 0; i < 40; i++) {
                g.setColor(randomColor(random, 100, 200));
                int x = random.nextInt(IMAGE_WIDTH);
                int yy = random.nextInt(IMAGE_HEIGHT);
                g.fillRect(x, yy, 1, 1);
            }
        } finally {
            g.dispose();
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            // ByteArrayOutputStream 不会真正抛 IOException，转成受检异常纯属签名要求
            throw new UncheckedIOException(e);
        }
    }

    private static Color randomColor(ThreadLocalRandom random, int min, int max) {
        int r = min + random.nextInt(max - min);
        int g = min + random.nextInt(max - min);
        int b = min + random.nextInt(max - min);
        return new Color(r, g, b);
    }
}
