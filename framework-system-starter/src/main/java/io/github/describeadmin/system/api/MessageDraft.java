package io.github.describeadmin.system.api;

import java.util.regex.Pattern;

/**
 * 一条待发送的站内信，由业务代码构造后交给 {@link MessageSender#send}。
 *
 * <p>校验全部在构造期完成，违规直接抛 {@link IllegalArgumentException}，<b>不截断</b>——
 * 这些字段都来自业务代码而非终端用户，超长是调用方的编程错误，静默截断只会让问题晚一步、
 * 换个样子出现。业务方若把用户输入（如留言内容）放进 {@code content}，长度应在自己的接口层先校验。
 *
 * @param category 分类，业务自定，建议 {@code <模块>.<事件>}（如 {@code workorder.approval}）。
 *                 框架不预置任何分类；前端按字典 {@code sys_message_category} 显示中文名，
 *                 字典里没登记的分类原样显示编码。必填，≤64
 * @param title    标题，必填，≤128
 * @param content  正文，<b>纯文本</b>（前端插值渲染，不解析 HTML），可空，≤500
 * @param link     点击后跳转的<b>站内路由路径</b>，可带 query（如 {@code /workorder/detail?id=123}），可空，≤255。
 *                 只接受以单个 {@code /} 开头的路径，外链与协议相对地址一律拒绝，见 {@link #LINK_PATTERN}
 * @param bizKey   业务键，可空，≤64。与 {@code category} 一起供 {@link MessageSender#markReadByBiz} 定位
 * @param senderId 发送人用户 ID，{@code null} 表示系统消息
 */
public record MessageDraft(String category, String title, String content,
                           String link, String bizKey, Long senderId) {

    public static final int MAX_CATEGORY_LENGTH = 64;
    public static final int MAX_TITLE_LENGTH = 128;
    public static final int MAX_CONTENT_LENGTH = 500;
    public static final int MAX_LINK_LENGTH = 255;
    public static final int MAX_BIZ_KEY_LENGTH = 64;

    /**
     * 站内路径：单个 {@code /} 开头，第二个字符不是 {@code /} 或 {@code \}，全程不含空白与反斜杠。
     *
     * <p>挡掉 {@code https://evil}（不以 {@code /} 开头）、{@code //evil}（协议相对地址）、
     * {@code /\evil}（部分浏览器把 {@code \} 当 {@code /}，等同协议相对地址）。
     * 消息是给收件人点的，放行外链等于给钓鱼链接盖了"系统消息"的章。
     */
    public static final Pattern LINK_PATTERN = Pattern.compile("^/(?![/\\\\])[^\\s\\\\]*$");

    public MessageDraft {
        requireText(category, "category", MAX_CATEGORY_LENGTH);
        requireText(title, "title", MAX_TITLE_LENGTH);
        checkLength(content, "content", MAX_CONTENT_LENGTH);
        checkLength(bizKey, "bizKey", MAX_BIZ_KEY_LENGTH);
        if (link != null && !link.isEmpty()) {
            checkLength(link, "link", MAX_LINK_LENGTH);
            if (!LINK_PATTERN.matcher(link).matches()) {
                throw new IllegalArgumentException("站内信 link 只接受站内路由路径（以单个 / 开头）: " + link);
            }
        } else {
            link = null;
        }
        if (bizKey != null && bizKey.isEmpty()) {
            bizKey = null;
        }
    }

    /** 系统消息的便捷写法：无业务键、无发送人。 */
    public static MessageDraft of(String category, String title, String content, String link) {
        return new MessageDraft(category, title, content, link, null, null);
    }

    private static void requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("站内信 " + field + " 不能为空");
        }
        checkLength(value, field, max);
    }

    private static void checkLength(String value, String field, int max) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(
                    "站内信 " + field + " 超长: " + value.length() + " > " + max);
        }
    }
}
