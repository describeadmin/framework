package io.github.describeadmin.web.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TraceIdFilter")
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter(0);

    @Test
    @DisplayName("合规的上游 traceId 原样沿用")
    void reusesWellFormedUpstreamId() throws Exception {
        assertThat(traceIdFor("a1b2-c3_d4")).isEqualTo("a1b2-c3_d4");
    }

    @Test
    @DisplayName("含换行、空格或超过 64 位的上游 traceId 不沿用，换成新生成的 32 位十六进制")
    void replacesMalformedUpstreamId() throws Exception {
        for (String bad : new String[] {"abc\nINFO forged log line", "has space", "x".repeat(65), "<script>"}) {
            assertThat(traceIdFor(bad)).as(bad).matches("[0-9a-f]{32}");
        }
    }

    @Test
    @DisplayName("没有上游 traceId 时生成一个")
    void generatesWhenAbsent() throws Exception {
        assertThat(traceIdFor(null)).matches("[0-9a-f]{32}");
    }

    private String traceIdFor(String upstream) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (upstream != null) {
            request.addHeader(TraceIdFilter.TRACE_ID_HEADER, upstream);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
    }
}
