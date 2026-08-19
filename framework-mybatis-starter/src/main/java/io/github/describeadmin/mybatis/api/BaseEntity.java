package io.github.describeadmin.mybatis.api;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类，统一承担审计字段与逻辑删除。
 *
 * <p><b>业务实体不要重复定义这些字段。</b>
 *
 * <p>关于主键：这里刻意<b>不</b>指定 {@code @TableId(type = ...)}，让全局配置
 * {@code mybatis-plus.global-config.db-config.id-type} 生效（默认 {@code AUTO} 自增）。
 * 需要切换为雪花 ID 的场景（多地数据汇总、跨库迁移、分布式数据库）改配置即可，
 * 不必改代码。见 develop_plan.md 2.3.1。
 *
 * <p>关于字段类型：审计时间用 {@link LocalDateTime} 而非 {@code Timestamp}，
 * 避免跨时区行为差异；表结构上对应 {@code DATETIME}，不使用 {@code TIMESTAMP}
 * ——后者在 MySQL 5.7 与部分国产化库上有 2038 年上限与自动更新语义的差异。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。
 */
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键。生成策略由全局配置决定，不在此硬编码。 */
    @TableId
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0 未删除，1 已删除。 */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    /** 乐观锁版本号。 */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCreateBy() {
        return createBy;
    }

    public void setCreateBy(Long createBy) {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(Long updateBy) {
        this.updateBy = updateBy;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
