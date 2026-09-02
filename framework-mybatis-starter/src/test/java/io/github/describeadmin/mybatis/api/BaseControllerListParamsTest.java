package io.github.describeadmin.mybatis.api;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BaseController} 列表查询取参方法（{@code text} / {@code asXxx}）的单元测试。
 *
 * <p>这些方法此前由 codegen 内联进每个带查询条件的 Controller，现上移到基类：
 * 空串语义、解析失败返回 400 是框架替业务方固化的口径，边界必须逐个钉死。
 * 同一个包，{@code protected static} 方法可直接调用。
 */
@DisplayName("BaseController 列表取参")
class BaseControllerListParamsTest {

    private static Map<String, String> params(String key, String value) {
        Map<String, String> m = new HashMap<>();
        m.put(key, value);
        return m;
    }

    @Nested
    @DisplayName("text：空串按未填处理")
    class Text {

        @Test
        @DisplayName("缺键返回 null")
        void missingKey() {
            assertThat(BaseController.text(Map.of(), "name")).isNull();
        }

        @Test
        @DisplayName("空串与纯空白都返回 null —— 前端清空输入框传的就是空串")
        void blankIsNull() {
            assertThat(BaseController.text(params("name", ""), "name")).isNull();
            assertThat(BaseController.text(params("name", "   "), "name")).isNull();
        }

        @Test
        @DisplayName("正常值去掉首尾空白")
        void trims() {
            assertThat(BaseController.text(params("name", "  张三 "), "name")).isEqualTo("张三");
        }
    }

    @Nested
    @DisplayName("asXxx：空值放行、合法值转换、非法值 400")
    class Parsers {

        @Test
        @DisplayName("空值返回 null，不参与筛选")
        void blankReturnsNull() {
            assertThat(BaseController.asInt(params("n", ""), "n")).isNull();
            assertThat(BaseController.asLong(Map.of(), "n")).isNull();
            assertThat(BaseController.asDecimal(Map.of(), "n")).isNull();
            assertThat(BaseController.asDate(Map.of(), "n")).isNull();
            assertThat(BaseController.asDateTime(Map.of(), "n")).isNull();
        }

        @Test
        @DisplayName("合法值按类型转换")
        void parsesValidValues() {
            assertThat(BaseController.asInt(params("n", "42"), "n")).isEqualTo(42);
            assertThat(BaseController.asLong(params("n", "9007199254740993"), "n"))
                    .isEqualTo(9007199254740993L);
            assertThat(BaseController.asDecimal(params("n", "3.14"), "n"))
                    .isEqualByComparingTo(new BigDecimal("3.14"));
            assertThat(BaseController.asDate(params("n", "2026-09-01"), "n"))
                    .isEqualTo(LocalDate.of(2026, 9, 1));
        }

        @Test
        @DisplayName("asDateTime 同时接受 T 与空格分隔")
        void dateTimeAcceptsBothSeparators() {
            LocalDateTime expected = LocalDateTime.of(2026, 9, 1, 8, 30, 0);
            assertThat(BaseController.asDateTime(params("n", "2026-09-01T08:30:00"), "n"))
                    .isEqualTo(expected);
            assertThat(BaseController.asDateTime(params("n", "2026-09-01 08:30:00"), "n"))
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("格式非法抛 BizException(BAD_REQUEST)，而不是冒泡成 500")
        void malformedIsClientError() {
            assertThatThrownBy(() -> BaseController.asInt(params("n", "abc"), "n"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("参数格式不正确")
                    .hasMessageContaining("n=abc")
                    .extracting(e -> ((BizException) e).getCode())
                    .isEqualTo(ResultCode.BAD_REQUEST.getCode());

            assertThatThrownBy(() -> BaseController.asDate(params("n", "2026/09/01"), "n"))
                    .isInstanceOf(BizException.class);
        }
    }
}
