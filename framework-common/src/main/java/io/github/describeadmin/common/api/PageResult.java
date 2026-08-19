package io.github.describeadmin.common.api;

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

    public long getTotal() {
        return total;
    }

    public long getCurrent() {
        return current;
    }

    public long getSize() {
        return size;
    }

    public long getPages() {
        return size <= 0 ? 0 : (total + size - 1) / size;
    }
}
