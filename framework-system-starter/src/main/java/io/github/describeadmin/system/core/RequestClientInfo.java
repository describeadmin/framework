package io.github.describeadmin.system.core;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 从 {@link HttpServletRequest} 提取"这次请求从哪来"的信息：客户端 IP 与设备描述。
 *
 * <p>{@code core/} 包、非 {@code api/} 包——不在兼容性承诺范围内，只服务于 {@code framework-system-starter}
 * 自己的两个用途：{@link OperLogAspect} 记操作日志的来源 IP，和 {@code AuthController} 登录时
 * 写进会话的 {@link io.github.describeadmin.security.api.SessionMeta}。两处对"哪个头算数"的
 * 判断必须一致，所以收在一个地方。
 *
 * <p>刻意不引 UA 解析库：那类库体积不小、规则表要持续更新，而这里只需要一个"大致是什么端"的
 * 印象。启发式覆盖不到的 UA 一律落到 {@code null}，由调用方决定怎么兜底。
 */
public final class RequestClientInfo {

    /** 设备描述的最大长度，够放"浏览器 · 操作系统"，也给未来可能直接存原始 UA 片段留余量。 */
    private static final int MAX_DEVICE_LENGTH = 200;

    private RequestClientInfo() {
    }

    /**
     * 客户端 IP。优先取反向代理透传的真实来源，取不到再落到直连地址。
     *
     * <p>{@code X-Forwarded-For} 可能是逗号分隔的链路，第一段才是最初的客户端；
     * 再退到 {@code X-Real-IP}（Nginx 常用）；最后才是 {@code getRemoteAddr()}
     * （没有代理时它就是对的，有代理时它是代理的地址）。
     */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 设备描述，形如 {@code "Chrome · Windows"} / {@code "Safari · iOS"}。
     *
     * <p>浏览器与操作系统各自识别，任意一侧识别不出就只给另一侧；两侧都识别不出返回
     * {@code null}。判断顺序有讲究——{@code Edg} 必须在 {@code Chrome} 之前（Edge 的 UA 同时含两者），
     * {@code Chrome} 必须在 {@code Safari} 之前（Chrome 的 UA 也含 {@code Safari}）。
     */
    public static String device(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ua = request.getHeader("User-Agent");
        if (!StringUtils.hasText(ua)) {
            return null;
        }
        String browser = browserOf(ua);
        String os = osOf(ua);
        String device;
        if (browser != null && os != null) {
            device = browser + " · " + os;
        } else if (browser != null) {
            device = browser;
        } else {
            device = os;
        }
        return device == null || device.length() <= MAX_DEVICE_LENGTH
                ? device
                : device.substring(0, MAX_DEVICE_LENGTH);
    }

    private static String browserOf(String ua) {
        if (ua.contains("Edg")) {
            return "Edge";
        }
        if (ua.contains("OPR") || ua.contains("Opera")) {
            return "Opera";
        }
        if (ua.contains("Chrome") || ua.contains("CriOS")) {
            return "Chrome";
        }
        if (ua.contains("Firefox") || ua.contains("FxiOS")) {
            return "Firefox";
        }
        if (ua.contains("Safari")) {
            return "Safari";
        }
        if (ua.contains("MSIE") || ua.contains("Trident")) {
            return "IE";
        }
        return null;
    }

    private static String osOf(String ua) {
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) {
            return "iOS";
        }
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            return "macOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return null;
    }
}
