package io.github.describeadmin.it;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * 集成测试用的 PoW 求解器：与前端 {@code solvePow} 同一套算法，独立实现而不复用
 * {@code PowCaptchaProvider} 的内部方法——这样它同时是"前后端哈希口径一致"的一份对照。
 */
final class PowSolver {

    private PowSolver() {
    }

    /**
     * @param challenge {@code GET /api/auth/captcha} 响应的 {@code data}（type 必须是 pow）
     * @return 答案，即 {@code captchaCode}
     */
    @SuppressWarnings("unchecked")
    static String solve(Map<String, Object> challenge) {
        if (!"pow".equals(challenge.get("type"))) {
            throw new IllegalStateException("测试前置条件：验证码类型应为 pow，实际为 " + challenge.get("type"));
        }
        Map<String, Object> payload = (Map<String, Object>) challenge.get("payload");
        String salt = (String) payload.get("salt");
        String target = (String) payload.get("challenge");
        // 必须是 JSON 数字：maxNumber 是 int，不受 Long 转字符串约定影响（CLAUDE.md 4.8）
        int max = (Integer) payload.get("maxNumber");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (long n = 0; n <= max; n++) {
                String hex = HexFormat.of().formatHex(
                        digest.digest((salt + n).getBytes(StandardCharsets.UTF_8)));
                if (hex.equals(target)) {
                    return String.valueOf(n);
                }
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("在 maxNumber 内无解，前后端哈希口径可能不一致");
    }
}
