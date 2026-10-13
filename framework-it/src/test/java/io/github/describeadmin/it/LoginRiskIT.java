package io.github.describeadmin.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 渐进式验证码的 IP 维度（HTTP 端到端）。
 *
 * <p>用独立的应用上下文（{@code properties} 不同即不复用），IP 阈值调到 {@link #IP_THRESHOLD}、
 * 全局维度关闭——其余 IT 为了不互相干扰把两个维度的阈值都抬到了碰不到的高度，见 test 的
 * {@code application.yml}。
 *
 * <p><b>只有一个用例且顺序敏感</b>：IP 计数在本上下文内跨用例累积、且登录成功不清零，
 * 拆成多个用例会让执行顺序决定结果。建号要在喷洒之前做完——管理员登录同样来自 127.0.0.1，
 * 喷洒之后它也会被要求验证码。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "describeadmin.security.captcha.ip.threshold=" + LoginRiskIT.IP_THRESHOLD,
        "describeadmin.security.captcha.global.enabled=false"
})
@DisplayName("渐进式验证码：IP 维度（HTTP 端到端）")
class LoginRiskIT extends AbstractMySqlIntegrationTest {

    static final int IP_THRESHOLD = 3;

    @Autowired TestRestTemplate rest;

    @Test
    @DisplayName("密码喷洒：多个用户名各错一次，同一 IP 达到阈值后任何用户名都要求验证码，伪造 X-Forwarded-For 绕不过去")
    void passwordSprayingTriggersCaptchaByIp() {
        String victim = "risk-spray-victim";
        createUser(victim, "right-password-1");

        // 每个用户名只错一次——用户名维度（阈值 3）永远触发不了
        for (int i = 0; i < IP_THRESHOLD; i++) {
            ResponseEntity<String> resp = login(Map.of("username", "risk-spray-" + i, "password", "Admin@123"), null);
            assertThat(resp.getBody())
                    .as("第 " + (i + 1) + " 次失败仍是普通的认证失败")
                    .contains("\"code\":40102");
        }

        // 一个从未失败过的用户名、密码也是对的，照样要求验证码
        assertThat(login(Map.of("username", victim, "password", "right-password-1"), null).getBody())
                .as("IP 维度达到阈值后，对任何用户名都要求验证码")
                .contains("\"code\":40103");

        // 风控取的是直连地址：每次换一个伪造的 X-Forwarded-For 也绕不过去
        assertThat(login(Map.of("username", victim, "password", "right-password-1"), "203.0.113.7").getBody())
                .as("X-Forwarded-For 是客户端可伪造的，不能用于风控")
                .contains("\"code\":40103");

        // 带上正确的 PoW 解即可登录
        Map<String, Object> challenge = captchaData(rest.getForEntity("/api/auth/captcha", Map.class));
        Map<String, Object> body = new HashMap<>();
        body.put("username", victim);
        body.put("password", "right-password-1");
        body.put("captchaId", challenge.get("captchaId"));
        body.put("captchaCode", PowSolver.solve(challenge));
        ResponseEntity<String> success = login(body, null);
        assertThat(success.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(success.getBody()).contains("\"username\":\"" + victim + "\"");

        // 登录成功不清零 IP 计数：否则攻击者用一个自己的有效账号定期登录就能刷掉计数
        assertThat(login(Map.of("username", victim, "password", "right-password-1"), null).getBody())
                .contains("\"code\":40103");
    }

    // ------------------------------------------------------------------ 工具

    private ResponseEntity<String> login(Map<String, Object> fields, String forwardedFor) {
        Map<String, Object> body = new HashMap<>(fields);
        body.put("type", "password");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        if (forwardedFor != null) {
            headers.set("X-Forwarded-For", forwardedFor);
        }
        return rest.postForEntity("/api/auth/login", new HttpEntity<>(body, headers), String.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> captchaData(ResponseEntity<Map> resp) {
        return (Map<String, Object>) resp.getBody().get("data");
    }

    private void createUser(String username, String password) {
        ResponseEntity<Map> created = rest.exchange("/api/system/user/with-password",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "username", username,
                        "password", password,
                        "nickname", "风控测试账号",
                        "status", 1), bearer(tokenOfAdmin())),
                Map.class);
        assertThat(created.getStatusCode()).as("前置条件：建号应成功").isEqualTo(HttpStatus.OK);
        clearPwdResetRequired(username);
    }

    private String tokenOfAdmin() {
        ResponseEntity<Map> resp = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("type", "password", "username", "admin",
                        "password", devSeedAdminPassword()), jsonHeaders()),
                Map.class);
        assertThat(resp.getStatusCode()).as("登录应成功，检查种子数据").isEqualTo(HttpStatus.OK);
        return String.valueOf(((Map<?, ?>) resp.getBody().get("data")).get("token"));
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        return headers;
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
