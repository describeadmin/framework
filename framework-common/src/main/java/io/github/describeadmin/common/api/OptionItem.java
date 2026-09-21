package io.github.describeadmin.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 下拉框 / 树选择器的通用选项，跨模块引用数据的标准形状。
 *
 * <p><b>解决的问题</b>：管理端页面普遍需要别的模块的数据来填选择器（用户管理要角色与部门，
 * 角色管理要部门）。过去的做法是直接调对方的分页列表接口，带来两个结构性缺陷：
 * <ul>
 *   <li><b>假装不分页</b>——{@code ?current=1&size=200} 把分页接口当全量接口使，
 *       超出就静默丢数据。这个写法不可能靠调大参数修好，{@link PageQuery#MAX_SIZE}
 *       在入口就夹紧了；</li>
 *   <li><b>授权无法闭环</b>——只授「用户管理」的角色调 {@code /api/system/role} 会 403，
 *       而补授 {@code system:role:list} 又会让侧边栏多出「角色管理」入口。</li>
 * </ul>
 * 约定与分档见 CLAUDE.md 4.11。
 *
 * <p><b>平铺与树是同一个形状</b>：{@code children} 为空时不参与序列化，因此同一个 DTO
 * 同时供 {@code ElSelect} 与 {@code ElTreeSelect}/{@code ElTree} 使用，前端不必按控件分类型。
 * 树的组装用 {@link TreeBuilder#build}，不要另写一套。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。
 */
public final class OptionItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 单次返回的选项数上界。
     *
     * <p><b>撞上它说明该换远程搜索控件，而不是调大这个数。</b> 两千项的下拉框在界面上
     * 本来就不可用（滚动找不到、键盘过滤也慢），继续放大只是把「数据不全」换成「界面卡死」。
     * 平铺型端点应提供 {@code keyword} 参数让调用方收窄；树型端点无法搜索式裁剪
     * （树必须完整才能渲染），真到这个量级要改用懒加载子节点。
     */
    public static final int MAX_SIZE = 2000;

    private final String value;
    private final String label;
    private final Boolean disabled;

    /** 可变是为了配合 {@link TreeBuilder#build} 的 {@code childrenGetter} 往里追加子节点。 */
    private final List<OptionItem> children = new ArrayList<>();

    public OptionItem(String value, String label) {
        this(value, label, null);
    }

    public OptionItem(String value, String label, Boolean disabled) {
        this.value = value;
        this.label = label;
        this.disabled = disabled;
    }

    /**
     * 由实体 id 构造。
     *
     * <p><b>{@code value} 是 {@code String} 而不是 {@code Long}，这不是疏忽。</b>
     * 框架默认把所有 {@code Long} 序列化为字符串（见 CLAUDE.md 4.8，为了雪花 ID 不被前端
     * 静默舍入），所以前端拿到的本来就是字符串。DTO 直接声明成 {@code String}，
     * 免得读代码的人以为这里是那条规则的例外；前端也不必再为「合成的顶层节点写 0 还是 '0'」
     * 写一套 id 归一化（ElTreeSelect 的 v-model 按严格相等匹配，数字 0 配不上字符串 id）。
     */
    public static OptionItem of(Long id, String label) {
        return new OptionItem(id == null ? null : String.valueOf(id), label, null);
    }

    /** 同 {@link #of(Long, String)}，另带禁用态——停用的项仍要显示（否则编辑时回显为空），但不可选。 */
    public static OptionItem of(Long id, String label, boolean disabled) {
        return new OptionItem(id == null ? null : String.valueOf(id), label, disabled ? Boolean.TRUE : null);
    }

    /**
     * 校验候选集没有超过 {@link #MAX_SIZE}，超了直接抛错。
     *
     * <p><b>刻意抛错而不是截断。</b> 静默截断正是 {@code size=200} 这个 bug 的形态——
     * 症状是下拉里找不到某一项，而错误信息里没有任何东西指向「返回被裁掉了」。
     * 响亮地失败才能让人找到真因。
     *
     * <p>在<b>建树之前</b>对扁平列表调用，因此泛型参数是源实体类型而非 {@code OptionItem}。
     *
     * <p>错误信息里<b>不写具体条数</b>：调用方通常只取 {@code MAX_SIZE + 1} 条来判断是否超限
     * （避免为了报个数把全表捞进内存），手上那个数字并不是真实总数，写出来反而误导。
     *
     * @param items 扁平的候选集
     * @param what  出现在错误信息里的数据名称，如 {@code "角色"}
     * @return 原样返回 {@code items}，便于链式调用
     */
    public static <T> List<T> requireWithinLimit(List<T> items, String what) {
        if (items != null && items.size() > MAX_SIZE) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    what + "数量超过选项接口上限 " + MAX_SIZE + " 条。"
                            + "请用 keyword 收窄范围（已传则再多输入几个字）；"
                            + "若该数据本就是这个量级，应改用远程搜索控件而不是一次性下拉。");
        }
        return items;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    /** {@code null} 时不序列化，免得每个选项都多一个 {@code "disabled": false}。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getDisabled() {
        return disabled;
    }

    /** 平铺场景下为空，不参与序列化——前端据此区分不出来，也不需要区分。 */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<OptionItem> getChildren() {
        return children;
    }
}
