package io.github.describeadmin.storage.api;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

/**
 * 文件存储契约。
 *
 * <p><b>方法刻意保持得很少</b>：与 {@code CacheProvider} 同样的取舍——每多一个方法，
 * 将来每种实现都要正确实现一遍。需要新能力时用 {@code default} 方法追加，不破坏既有实现。
 *
 * <p><b>读写两类方法对非法输入的态度不对称</b>：{@link #get} 与 {@link #exists}
 * 对不存在或不合法的 {@code key} 一律返回空值/{@code false}，不抛异常——这与
 * {@code CacheProvider.get} 的"未命中不是错误"是同一原则。{@link #put} 与
 * {@link #remove} 则相反，对非法 {@code key} 直接抛 {@link IllegalArgumentException}——
 * 未命中是正常流量，畸形的写入请求是调用方的 bug。
 *
 * <p><b>{@code key} 的约定</b>：以 {@code /} 分隔的相对路径，不含 {@code ..} 片段，
 * 不以 {@code /} 开头。这个约束是为本地磁盘实现声明的——本地磁盘需要把 key 解析成
 * 文件系统路径，因此必须防御路径穿越；未来以对象存储为后端的实现（其命名空间本身是扁平的）
 * 天然满足这个约束，不受影响。
 *
 * <p><b>{@link #put} 语义为同 key 覆盖</b>，与 {@code CacheProvider.put} 一致，也对齐
 * 对象存储服务 PutObject 的天然语义。需要"仅当不存在时才写入"的调用方应自行先调用
 * {@link #exists}——这是业务层策略，不是存储契约本身要关心的事。
 *
 * <p>框架默认提供 {@code LocalFileStorageProvider}。业务方注册自己的 {@code StorageProvider}
 * Bean 即自动覆盖。
 *
 * <p>本接口位于 {@code api} 包下，属于兼容性承诺范围。
 */
public interface StorageProvider {

    /**
     * 写入一个对象，同 key 覆盖。
     *
     * @param key         对象键，不为空；约定见类注释
     * @param content     内容流，方法内部会读取完毕但不负责关闭——关闭由调用方负责
     * @param size         内容字节数；供需要提前知道 Content-Length 的实现（如分片上传）使用，
     *                     实现不要求必须以此为准——本地磁盘实现以实际落盘字节数为准
     * @param contentType MIME 类型，可为 {@code null}
     * @return 写入结果，包含可用于后续 {@link #get}/{@link #url} 的 key
     */
    StorageObject put(String key, InputStream content, long size, String contentType);

    /** 读取内容流。key 不存在或不合法时返回空 {@link Optional}，不抛异常。 */
    Optional<InputStream> get(String key);

    /** 对象是否存在。key 不合法时返回 {@code false}，不抛异常。 */
    boolean exists(String key);

    /** 删除。key 不存在时静默返回。 */
    void remove(String key);

    /**
     * 返回对象的访问地址。
     *
     * <p>这是一个虚拟路径字符串，能否通过 HTTP 实际访问到，取决于具体实现与部署方式——
     * 本接口本身不做任何承诺。
     */
    String url(String key);

    /**
     * 返回一个限时有效的访问地址（预签名 URL）。
     *
     * <p>默认实现直接退化为 {@link #url(String)}，忽略 {@code expiry}——本地磁盘等
     * 没有"签名"概念的实现无需关心这个方法，也不需要重写它。以对象存储为后端的实现
     * （S3、阿里云 OSS 等）应当重写本方法，返回真正带签名、到期后失效的地址——
     * 这类存储服务的真实部署几乎总是私有桶，{@link #url} 返回的公开地址通常并不可访问，
     * {@code presignedUrl} 才是业务方实际应该使用的下载/预览地址。
     *
     * <p>之所以现在就在 {@code api} 包下声明这个 {@code default} 方法，而不是等真正的
     * 对象存储插件落地时再加：一旦本接口发布，{@code api} 包下的签名变更就是 Breaking
     * Change（见 CLAUDE.md 2 与 5）；用 {@code default} 方法可以随时新增而不破坏既有实现，
     * 但仅限于"新增"，改不了已有方法的语义——趁现在还没发布，把可预见的扩展点先占住，
     * 之后就不必再等一次小版本升级。
     *
     * @param key    对象键
     * @param expiry 有效期，必须为正数；对不支持签名的实现该参数被忽略
     */
    default String presignedUrl(String key, Duration expiry) {
        return url(key);
    }
}
