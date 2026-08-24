package io.github.describeadmin.system.core;

import java.util.List;
import java.util.function.Function;

/**
 * @deprecated 已上提为 {@link io.github.describeadmin.common.api.TreeBuilder}，
 * 建树是业务方也会用到的通用能力，留在系统管理模块的 {@code core} 包里拿不到。
 * 本类是 0.2.0 的过渡转发，将在 0.3.0 移除。
 */
@Deprecated(since = "0.2.0", forRemoval = true)
public final class TreeBuilder {

    private TreeBuilder() {
    }

    /** @deprecated 改用 {@link io.github.describeadmin.common.api.TreeBuilder#build}。 */
    @Deprecated(since = "0.2.0", forRemoval = true)
    public static <T> List<T> build(List<T> flat,
                                    Function<T, Long> idGetter,
                                    Function<T, Long> parentIdGetter,
                                    Function<T, List<T>> childrenGetter) {
        return io.github.describeadmin.common.api.TreeBuilder.build(
                flat, idGetter, parentIdGetter, childrenGetter);
    }
}
