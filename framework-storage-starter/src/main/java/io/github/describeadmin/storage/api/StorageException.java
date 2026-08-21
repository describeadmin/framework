package io.github.describeadmin.storage.api;

/**
 * {@link StorageProvider} 实现在遇到不可恢复的 I/O 失败（磁盘写入失败、网络异常等）时抛出。
 *
 * <p>不要用它包装"key 不合法"这类调用方错误——那属于 {@link IllegalArgumentException}
 * 的职责，见 {@link StorageProvider} 类注释里"读写两类方法对非法输入的态度不对称"一节。
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
