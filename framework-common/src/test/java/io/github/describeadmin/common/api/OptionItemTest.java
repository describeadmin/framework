package io.github.describeadmin.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OptionItem} 的形状与上限行为。
 *
 * <p>这两条都是「写错了也能跑、只在特定数据量/特定前端控件上才炸」的那一类，
 * 所以断言比对具体值而不只是数量。
 */
@DisplayName("OptionItem 选项形状")
class OptionItemTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("value 是字符串——雪花 ID 不能以数字形态到前端")
    void serializesIdAsString() throws Exception {
        // 19 位，超过 JS 的 Number.MAX_SAFE_INTEGER（2^53 ≈ 9.007e15）
        OptionItem item = OptionItem.of(1839274650192837465L, "研发部");

        assertThat(item.getValue()).isEqualTo("1839274650192837465");
        assertThat(mapper.writeValueAsString(item))
                .contains("\"value\":\"1839274650192837465\"")
                .contains("\"label\":\"研发部\"");
    }

    @Test
    @DisplayName("平铺项不序列化 children 与 disabled，免得每个选项都拖两个空字段")
    void omitsEmptyChildrenAndNullDisabled() throws Exception {
        String json = mapper.writeValueAsString(OptionItem.of(1L, "管理员"));

        assertThat(json).isEqualTo("{\"value\":\"1\",\"label\":\"管理员\"}");
    }

    @Test
    @DisplayName("停用项带 disabled:true——要能显示但不可选，不是不下发")
    void marksDisabled() throws Exception {
        String json = mapper.writeValueAsString(OptionItem.of(2L, "已撤销的部门", true));

        assertThat(json).contains("\"disabled\":true");
    }

    @Test
    @DisplayName("children 非空时才出现，且是嵌套形状")
    void serializesChildrenWhenPresent() throws Exception {
        OptionItem parent = OptionItem.of(1L, "总公司");
        parent.getChildren().add(OptionItem.of(2L, "研发部"));

        String json = mapper.writeValueAsString(parent);

        assertThat(json).isEqualTo(
                "{\"value\":\"1\",\"label\":\"总公司\",\"children\":[{\"value\":\"2\",\"label\":\"研发部\"}]}");
    }

    @Test
    @DisplayName("恰好到上限放行——边界不能差一")
    void allowsExactlyMaxSize() {
        List<String> items = nItems(OptionItem.MAX_SIZE);

        assertThat(OptionItem.requireWithinLimit(items, "角色")).isSameAs(items);
    }

    @Test
    @DisplayName("超过上限抛错而不是截断——静默截断正是 size=200 那个 bug 的形态")
    void rejectsOverLimit() {
        List<String> items = nItems(OptionItem.MAX_SIZE + 1);

        assertThatThrownBy(() -> OptionItem.requireWithinLimit(items, "角色"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("角色")
                .hasMessageContaining(String.valueOf(OptionItem.MAX_SIZE))
                .hasMessageContaining("keyword")
                // 手上的条数是"上限+1"的探测值，不是真实总数，写进错误信息会误导
                .hasMessageNotContaining(String.valueOf(OptionItem.MAX_SIZE + 1));
    }

    @Test
    @DisplayName("null 与空集合放行，调用方不必先判空")
    void toleratesNullAndEmpty() {
        assertThat(OptionItem.requireWithinLimit(null, "角色")).isNull();
        assertThat(OptionItem.requireWithinLimit(List.<String>of(), "角色")).isEmpty();
    }

    private static List<String> nItems(int n) {
        return IntStream.range(0, n).mapToObj(String::valueOf).toList();
    }
}
