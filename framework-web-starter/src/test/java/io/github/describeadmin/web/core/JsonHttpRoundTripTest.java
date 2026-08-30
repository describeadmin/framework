package io.github.describeadmin.web.core;

import io.github.describeadmin.common.api.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 跑通真实的 HTTP 链路：{@code @RequestBody} 解析 → 控制器 → 响应序列化。
 *
 * <p><b>为什么不能只靠 {@code ObjectMapper} 单测</b>：单测验证的是模块本身，
 * 而线上真正出问题的位置是"框架的 Module 有没有被 Spring MVC 的
 * {@code MappingJackson2HttpMessageConverter} 用上"。Module Bean 建了却没被装上，
 * 是一种启动毫无异常的静默失败——只有走一次真实请求才能排除。
 *
 * <p>{@link #acceptsSpaceSeparatedDateTimeInBody()} 覆盖的是一个<b>本批修复前真实存在的
 * bug</b>：codegen 生成的日期时间选择器发的是 {@code yyyy-MM-dd HH:mm:ss}，
 * 而 Spring Boot 默认按 ISO-8601 解析，任何带 {@code datetime} 字段的模块，
 * 新增/编辑表单一提交就失败。实测复现（把 {@code describeadmin.web.json.enabled}
 * 关掉再发同样的请求）返回的是 <b>500</b>——{@code GlobalExceptionHandler} 没有
 * 单独处理 {@code HttpMessageNotReadableException}，解析失败落到 Throwable 兜底。
 */
@SpringBootTest(classes = JsonHttpRoundTripTest.TestApp.class)
@AutoConfigureMockMvc
@DisplayName("JSON 约定的 HTTP 链路")
class JsonHttpRoundTripTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("请求体里的空格分隔时间能解析——这正是修复前会 500 的那条路径")
    void acceptsSpaceSeparatedDateTimeInBody() throws Exception {
        mockMvc.perform(post("/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"1234567890123456789\","
                                + "\"dateTime\":\"2026-08-24 15:30:00\","
                                + "\"date\":\"2026-08-24\"}"))
                .andExpect(status().isOk())
                // 回显即证明绑定成功且值正确，不是"解析成了别的时刻"
                .andExpect(jsonPath("$.dateTime").value("2026-08-24 15:30:00"))
                .andExpect(jsonPath("$.date").value("2026-08-24"));
    }

    @Test
    @DisplayName("请求体里的 ISO 时间同样能解析——不打断已经在发 ISO 的调用方")
    void stillAcceptsIsoDateTimeInBody() throws Exception {
        mockMvc.perform(post("/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"1\",\"dateTime\":\"2026-08-24T15:30:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateTime").value("2026-08-24 15:30:00"));
    }

    @Test
    @DisplayName("雪花 ID 走完整个 HTTP 往返仍逐字符保真")
    void snowflakeIdSurvivesHttpRoundTrip() throws Exception {
        mockMvc.perform(post("/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"1234567890123456789\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1234567890123456789"));
    }

    @Test
    @DisplayName("分页响应：records 里的 id 是字符串，total 仍是数字")
    void pageResponseKeepsMetadataNumeric() throws Exception {
        mockMvc.perform(get("/page"))
                .andExpect(status().isOk())
                // 字符串与数字在 JSON 文本里的区别就是那对引号，直接比对原文最不含糊
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"id\":\"1234567890123456789\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"total\":25")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"pages\":3")));
    }

    @SpringBootApplication
    static class TestApp {

        @RestController
        static class EchoController {

            @PostMapping("/echo")
            Payload echo(@RequestBody Payload payload) {
                return payload;
            }

            @GetMapping("/page")
            PageResult<Payload> page() {
                Payload row = new Payload();
                row.setId(1234567890123456789L);
                return new PageResult<>(List.of(row), 25L, 2L, 10L);
            }
        }
    }

    static class Payload {
        private Long id;
        private LocalDateTime dateTime;
        private LocalDate date;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public LocalDateTime getDateTime() {
            return dateTime;
        }

        public void setDateTime(LocalDateTime dateTime) {
            this.dateTime = dateTime;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }
    }
}
