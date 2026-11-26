package io.github.describeadmin.system.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内信。每个收件人一行。
 *
 * <p><b>不继承 {@link io.github.describeadmin.mybatis.api.BaseEntity}</b>，与 {@link SysOperLog} 同类判断：
 * <ul>
 *   <li>{@code version} 会和"全部已读""按业务键标已读"这类批量更新冲突；</li>
 *   <li>{@code create_by} 由 {@code MetaObjectHandler} 按当前请求用户填充，系统发送或异步发送时
 *       没有意义，发送人已由 {@link #senderId} 表达。</li>
 * </ul>
 * 写入由 {@code SysMessageService} 一次性控制，不需要自动填充。
 */
@TableName("sys_message")
public class SysMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 收件人。 */
    private Long userId;

    /** 发送人，{@code null} 表示系统消息。 */
    private Long senderId;

    private String category;
    private String title;

    /** 纯文本。 */
    private String content;

    /** 站内路由路径，可带 query。 */
    private String link;

    private String bizKey;

    /** 已读时间，{@code null} 表示未读。 */
    private LocalDateTime readTime;

    private LocalDateTime createTime;

    /** 删除标记，初始值由数据库填充。 */
    @JsonIgnore
    @TableLogic(value = "0", delval = "1")
    @TableField(insertStrategy = FieldStrategy.NEVER)
    private Integer deleted;

    /** 非持久化字段：发送人昵称，供前端展示。 */
    @TableField(exist = false)
    private String senderName;

    /** 非持久化字段：发送人头像的对象键，供前端展示。 */
    @TableField(exist = false)
    private String senderAvatar;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public String getBizKey() { return bizKey; }
    public void setBizKey(String bizKey) { this.bizKey = bizKey; }
    public LocalDateTime getReadTime() { return readTime; }
    public void setReadTime(LocalDateTime readTime) { this.readTime = readTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public String getSenderAvatar() { return senderAvatar; }
    public void setSenderAvatar(String senderAvatar) { this.senderAvatar = senderAvatar; }
}
