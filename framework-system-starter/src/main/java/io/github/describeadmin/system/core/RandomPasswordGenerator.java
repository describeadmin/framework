package io.github.describeadmin.system.core;

import io.github.describeadmin.security.api.PasswordPolicy;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 生成一个满足当前 {@link PasswordPolicy} 的随机强口令，供 {@link DevAdminSeeder} 使用。
 *
 * <p>刻意排除易混淆字符（{@code 0O 1lI}）——有人会照着 {@code .passwd} 手敲这个口令。
 * 每类字符至少一个，长度 16，最后打乱顺序。
 */
final class RandomPasswordGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGIT = "23456789";
    private static final String SYMBOL = "!@#$%^&*-_=+";
    private static final String ALL = LOWER + UPPER + DIGIT + SYMBOL;

    private static final int LENGTH = 16;
    private static final int MAX_ATTEMPTS = 100;

    private RandomPasswordGenerator() {
    }

    static String generate(PasswordPolicy policy, String username) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = build();
            try {
                policy.validate(candidate, username);
                return candidate;
            } catch (RuntimeException policyRejected) {
                // 极小概率：随机结果不满足业务方自定义的策略（如黑名单），换一个再试
            }
        }
        throw new IllegalStateException(
                "dev-seed: 连续 " + MAX_ATTEMPTS + " 次都没生成出满足 PasswordPolicy 的随机口令");
    }

    private static String build() {
        List<Character> chars = new ArrayList<>(LENGTH);
        chars.add(pick(LOWER));
        chars.add(pick(UPPER));
        chars.add(pick(DIGIT));
        chars.add(pick(SYMBOL));
        while (chars.size() < LENGTH) {
            chars.add(pick(ALL));
        }
        Collections.shuffle(chars, RANDOM);
        StringBuilder sb = new StringBuilder(LENGTH);
        for (char c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static char pick(String pool) {
        return pool.charAt(RANDOM.nextInt(pool.length()));
    }
}
