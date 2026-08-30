package io.github.describeadmin.storage.core;

import io.github.describeadmin.storage.api.StorageException;
import io.github.describeadmin.storage.api.StorageObject;
import io.github.describeadmin.storage.api.StorageProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * {@link StorageProvider} 的本地磁盘实现，框架默认值。
 *
 * <p><b>适用范围与已知局限（部署前必读）</b>：
 * <ul>
 *   <li>数据落在本机磁盘，<b>不支持多实例部署</b>——实例 A 写入的对象在实例 B 上读不到</li>
 *   <li>{@link #url(String)} 返回的只是拼接出来的虚拟路径字符串。本模块不依赖
 *       {@code spring-boot-starter-web}，不注册任何静态资源映射或下载端点——
 *       这个地址能否真正通过 HTTP 访问到，取决于业务方是否自行配置了静态资源映射，
 *       或反向代理规则</li>
 *   <li>{@code get} 不会把 {@code put} 时传入的 {@code contentType} 保存下来
 *       并在下次读取时带回——本地磁盘没有元数据边车，重启后这份信息就没了</li>
 * </ul>
 * 与 {@code InMemoryCacheProvider} 是同一组取舍：单机部署下可接受，需要多实例
 * 或需要真正对外提供下载能力时换成以对象存储为后端的插件实现，上层代码不用动。
 */
public class LocalFileStorageProvider implements StorageProvider {

    private static final Pattern WINDOWS_DRIVE_LETTER = Pattern.compile("^[A-Za-z]:.*");

    private final Path rootDir;
    private final String urlPrefix;
    private final boolean overwriteExisting;

    public LocalFileStorageProvider(String rootDir, String urlPrefix, boolean overwriteExisting) {
        if (rootDir == null || rootDir.isBlank()) {
            throw new IllegalArgumentException("存储根目录不能为空");
        }
        this.rootDir = Paths.get(rootDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootDir);
        } catch (IOException e) {
            throw new StorageException("无法创建存储根目录: " + this.rootDir, e);
        }
        this.urlPrefix = normalizePrefix(urlPrefix);
        this.overwriteExisting = overwriteExisting;
    }

    @Override
    public StorageObject put(String key, InputStream content, long size, String contentType) {
        if (content == null) {
            throw new IllegalArgumentException("写入内容不能为 null");
        }
        Path target = resolveForWrite(key);
        try {
            Files.createDirectories(target.getParent());
            if (!overwriteExisting && Files.exists(target)) {
                throw new StorageException("对象已存在且未启用覆盖: " + key);
            }
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            // 以磁盘实际字节数为准，不信任调用方传入的 size——那只是给需要提前知道
            // Content-Length 的实现（如分片上传）用的元数据，不保证与实际写入一致
            long actualSize = Files.size(target);
            return new StorageObject(key, url(key), actualSize, contentType);
        } catch (IOException e) {
            throw new StorageException("写入失败: " + key, e);
        }
    }

    @Override
    public Optional<InputStream> get(String key) {
        Optional<Path> target = resolveForRead(key).filter(Files::isRegularFile);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.newInputStream(target.get()));
        } catch (IOException e) {
            // 走到这里说明 key 合法且文件确实存在，读取失败是真正的 I/O 故障，
            // 与"未命中"是两回事，要抛出去而不是吞掉
            throw new StorageException("读取失败: " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return resolveForRead(key).filter(Files::isRegularFile).isPresent();
    }

    @Override
    public void remove(String key) {
        Path target = resolveForWrite(key);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new StorageException("删除失败: " + key, e);
        }
    }

    @Override
    public String url(String key) {
        return key == null ? null : urlPrefix + key;
    }

    /** 写入路径：key 不合法直接抛异常——畸形的写入请求是调用方的 bug。 */
    private Path resolveForWrite(String key) {
        validateKeyFormat(key);
        Path target = rootDir.resolve(key).normalize();
        if (!target.startsWith(rootDir)) {
            throw new IllegalArgumentException("非法的对象键（越界访问存储根目录）: " + key);
        }
        return target;
    }

    /** 读取路径：key 不合法一律按未命中处理，不抛异常——与 CacheProvider.get 同一原则。 */
    private Optional<Path> resolveForRead(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        try {
            validateKeyFormat(key);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        Path target = rootDir.resolve(key).normalize();
        if (!target.startsWith(rootDir)) {
            return Optional.empty();
        }
        return Optional.of(target);
    }

    /**
     * key 格式校验。在解析成文件系统路径之前先做字符串层面的拒绝，而不是只依赖
     * {@code Path.normalize()} 之后的 {@code startsWith} 兜底——Windows 的默认文件系统
     * 对 {@code \} 与盘符前缀的处理方式不完全等同于 Unix 路径分隔符，
     * 光靠 normalize + startsWith 不足以覆盖所有绕过手法。
     */
    private static void validateKeyFormat(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("对象键不能为空");
        }
        if (key.startsWith("/") || key.startsWith("\\")) {
            throw new IllegalArgumentException("对象键不能以路径分隔符开头: " + key);
        }
        if (key.contains("\\")) {
            throw new IllegalArgumentException("对象键不能包含反斜杠: " + key);
        }
        if (WINDOWS_DRIVE_LETTER.matcher(key).matches()) {
            throw new IllegalArgumentException("对象键不能包含盘符: " + key);
        }
        for (String segment : key.split("/")) {
            if (segment.equals("..")) {
                throw new IllegalArgumentException("对象键不能包含 .. 片段: " + key);
            }
        }
    }

    private static String normalizePrefix(String urlPrefix) {
        if (urlPrefix == null || urlPrefix.isBlank()) {
            return "";
        }
        return urlPrefix.endsWith("/") ? urlPrefix : urlPrefix + "/";
    }
}
