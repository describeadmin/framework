package io.github.describeadmin.common.api;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 分页查询结果。
 *
 * <p>刻意不直接向外暴露 MyBatis-Plus 的 {@code IPage}——那会让前端契约与 ORM 实现绑死，
 * 将来换 ORM 或调整分页实现都会变成 Breaking Change。
 *
 * <p><b>四个分页元信息字段是全局「Long 序列化为 String」规则的唯一例外</b>，
 * 靠 {@code @JsonFormat(shape = NUMBER)} 显式排除。框架默认把所有 {@code Long}/{@code long}
 * 序列化成字符串，是为了让雪花 ID（19 位，超过 JS 的 {@code Number.MAX_SAFE_INTEGER}）
 * 不被前端静默舍入；但 {@code total}/{@code current}/{@code size}/{@code pages} 不可能
 * 接近 2^53，而前端的分页组件（如 Element Plus 的 {@code el-pagination}）要求 {@code :total}
 * 是数字，转成字符串只有坏处没有好处。
 *
 * <p>这同时是给业务方看的逃生舱示范：自己的 {@code Long} 字段不想被转成字符串时，
 * 用同一个注解排除即可。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。
 *
 * @param <T> 记录类型
 */
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<T> records;
    private final long total;
    private final long current;
    private final long size;

    public PageResult(List<T> records, long total, long current, long size) {
        this.records = records == null ? Collections.emptyList() : List.copyOf(records);
        this.total = total;
        this.current = current;
        this.size = size;
    }

    public static <T> PageResult<T> empty(long current, long size) {
        return new PageResult<>(Collections.emptyList(), 0L, current, size);
    }

    /** 把记录映射为另一种类型（如 Entity → VO），分页元信息保持不变。 */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(records.stream().map(mapper).map(r -> (R) r).toList(),
                total, current, size);
    }

    public List<T> getRecords() {
        return records;
    }

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    public long getTotal() {
        return total;
    }

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    public long getCurrent() {
        return current;
    }

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    public long getSize() {
        return size;
    }

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    public long getPages() {
        return size <= 0 ? 0 : (total + size - 1) / size;
    }
}
