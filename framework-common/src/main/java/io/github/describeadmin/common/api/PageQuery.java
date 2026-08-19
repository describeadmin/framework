package io.github.describeadmin.common.api;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页查询入参。
 *
 * <p>放在 framework-common 而非 mybatis-starter，是为了让只用 Web 层、不用本框架 ORM 的
 * 业务方也能复用同一套分页契约。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。
 */
public class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 单页最大条数的兜底上限，防止业务方传入超大 size 拖垮数据库。 */
    public static final long MAX_SIZE = 500L;

    private long current = 1L;
    private long size = 10L;

    public long getCurrent() {
        return current;
    }

    public void setCurrent(long current) {
        this.current = Math.max(1L, current);
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        // 在入口就夹紧，而不是依赖下游插件——业务方可能绕过分页插件直接用本对象
        this.size = Math.min(MAX_SIZE, Math.max(1L, size));
    }
}
