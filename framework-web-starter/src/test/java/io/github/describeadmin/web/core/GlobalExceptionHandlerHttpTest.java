package io.github.describeadmin.web.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 调用方的输入错误必须报成「请求参数错误」，不能落到兜底分支报 500。
 *
 * <p>报成 500 的后果是排查方向一开始就走偏：「日期填错了」「接口路径写错了」
 * 看起来都像服务器坏了。业务错误统一是 HTTP 200 + 错误码（见 {@link GlobalExceptionHandler} 类注释），
 * 只有路径不存在映射到 HTTP 404。
 */
@SpringBootTest(classes = GlobalExceptionHandlerHttpTest.TestApp.class)
@AutoConfigureMockMvc
@DisplayName("客户端输入错误不报 500")
class GlobalExceptionHandlerHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("请求体 JSON 语法错误")
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求体格式错误"));
    }

    @Test
    @DisplayName("字段格式错误时指出字段路径，且不泄露类名")
    void invalidFieldNamesThePathWithoutClassNames() throws Exception {
        mockMvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\",\"tags\":[{\"date\":\"2026-13-45\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求体格式错误：字段 tags[0].date"))
                .andExpect(content().string(not(containsString("io.github"))));
    }

    @Test
    @DisplayName("路径变量类型不对")
    void pathVariableTypeMismatchIsBadRequest() throws Exception {
        mockMvc.perform(get("/items/abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("参数格式错误：id"));
    }

    @Test
    @DisplayName("缺少必填查询参数")
    void missingParameterIsBadRequest() throws Exception {
        mockMvc.perform(get("/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("缺少参数：keyword"));
    }

    @Test
    @DisplayName("不存在的路径报 404，不是 500")
    void unknownPathIsNotFound() throws Exception {
        mockMvc.perform(get("/api/api/items"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("/api/api/items"));
    }

    @SpringBootApplication
    static class TestApp {

        @RestController
        static class ItemController {

            @PostMapping("/items")
            Item create(@RequestBody Item item) {
                return item;
            }

            @GetMapping("/items/{id}")
            Long byId(@PathVariable("id") Long id) {
                return id;
            }

            @GetMapping("/search")
            String search(@RequestParam("keyword") String keyword) {
                return keyword;
            }
        }
    }

    static class Item {
        private String name;
        private List<Tag> tags;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<Tag> getTags() {
            return tags;
        }

        public void setTags(List<Tag> tags) {
            this.tags = tags;
        }
    }

    static class Tag {
        private LocalDate date;

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }
    }
}
