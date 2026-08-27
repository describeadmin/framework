package io.github.describeadmin.system.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RequestClientInfo} 的单元测试。
 *
 * <p>纯函数，不起 Spring 上下文——只喂 {@link MockHttpServletRequest}。两块逻辑都值得守：
 * "哪个头算客户端 IP" 的优先级（写错会把代理地址当成来源），和 UA 启发式的判断顺序
 * （{@code Edg} 要在 {@code Chrome} 前、{@code Chrome} 要在 {@code Safari} 前）。
 */
@DisplayName("RequestClientInfo")
class RequestClientInfoTest {

    @Nested
    @DisplayName("clientIp")
    class ClientIp {

        @Test
        @DisplayName("X-Forwarded-For 取第一段，穿透多级代理")
        void forwardedForFirstHop() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1, 10.0.0.2");
            req.setRemoteAddr("10.0.0.2");

            assertThat(RequestClientInfo.clientIp(req)).isEqualTo("203.0.113.7");
        }

        @Test
        @DisplayName("没有 X-Forwarded-For 时退到 X-Real-IP")
        void fallsBackToRealIp() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("X-Real-IP", "198.51.100.9");
            req.setRemoteAddr("10.0.0.2");

            assertThat(RequestClientInfo.clientIp(req)).isEqualTo("198.51.100.9");
        }

        @Test
        @DisplayName("没有任何代理头时用直连地址")
        void fallsBackToRemoteAddr() {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRemoteAddr("192.168.1.20");

            assertThat(RequestClientInfo.clientIp(req)).isEqualTo("192.168.1.20");
        }

        @Test
        @DisplayName("null 请求返回 null，不抛异常")
        void nullRequest() {
            assertThat(RequestClientInfo.clientIp(null)).isNull();
        }
    }

    @Nested
    @DisplayName("device")
    class Device {

        private String device(String userAgent) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            if (userAgent != null) {
                req.addHeader("User-Agent", userAgent);
            }
            return RequestClientInfo.device(req);
        }

        @Test
        @DisplayName("Chrome on Windows")
        void chromeWindows() {
            assertThat(device("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"))
                    .isEqualTo("Chrome · Windows");
        }

        @Test
        @DisplayName("Edge 的 UA 同时含 Chrome/Safari，仍识别为 Edge")
        void edgeNotChrome() {
            assertThat(device("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"))
                    .isEqualTo("Edge · Windows");
        }

        @Test
        @DisplayName("Safari on iOS")
        void safariIos() {
            assertThat(device("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
                    + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"))
                    .isEqualTo("Safari · iOS");
        }

        @Test
        @DisplayName("识别不出浏览器时只给操作系统")
        void osOnly() {
            assertThat(device("Mozilla/5.0 (X11; Linux x86_64) Gecko/20100101 CustomAgent/2.0"))
                    .isEqualTo("Linux");
        }

        @Test
        @DisplayName("两侧都识别不出返回 null")
        void unknown() {
            assertThat(device("SomeCustomBot/1.0")).isNull();
        }

        @Test
        @DisplayName("没有 User-Agent 头返回 null")
        void missingHeader() {
            assertThat(device(null)).isNull();
        }
    }
}
