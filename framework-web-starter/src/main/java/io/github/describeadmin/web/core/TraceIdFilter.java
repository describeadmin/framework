package io.github.describeadmin.web.core;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪过滤器。
 *
 * <p>为每个请求生成（或沿用上游传入的）traceId，写入 MDC 供日志输出，并回写响应头，
 * 使前端、AI 自动化测试的网络日志与后端日志能够对应起来。
 *
 * <p>这一点对第五章的 AI 自动化测试很关键：测试失败时抓到的 network log 里带着 traceId，
 * 可以直接定位到后端日志，避免只能靠时间戳猜。
 */
public class TraceIdFilter extends OncePerRequestFilter implements Ordered {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final int order;

    public TraceIdFilter(int order) {
        this.order = order;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 容器线程会被复用，必须清理，否则 traceId 会串到下一个请求
            MDC.remove(TRACE_ID_KEY);
        }
    }

    @Override
    public int getOrder() {
        return order;
    }
}
