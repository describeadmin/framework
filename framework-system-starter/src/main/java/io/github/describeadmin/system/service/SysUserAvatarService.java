package io.github.describeadmin.system.service;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.CurrentUserProvider;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.storage.api.StorageProvider;
import io.github.describeadmin.system.core.AvatarImage;
import io.github.describeadmin.system.entity.SysUser;
import io.github.describeadmin.system.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用户头像。
 *
 * <p>库里 {@code sys_user.avatar} 只存对象键，文件本身交给 {@link StorageProvider}：
 * 默认存本地磁盘，引入对象存储插件后自动切过去，这里一行不用改。
 *
 * <p>对象键形如 {@code avatar/<userId>/<32 位十六进制>.<扩展名>}。读取端点免登录
 * （{@code <img src>} 不带令牌），所以键里的随机段是<b>唯一的访问控制</b>：拿不到键就枚举不出来。
 * 每次上传都换新键，这样读取端点可以放心让浏览器永久缓存。
 */
@Service
public class SysUserAvatarService {

    private static final Logger log = LoggerFactory.getLogger(SysUserAvatarService.class);

    /**
     * 单个头像文件的上限（字节）。与 Spring Boot 默认的 {@code spring.servlet.multipart.max-file-size}（1MB）一致。
     * 前端会先压缩到 256px 再上传，正常情况下只有几十 KB。
     */
    public static final long MAX_BYTES = 1024 * 1024;

    /**
     * 合法对象键的完整形状。读取端点只认它：一来把匿名可读的范围限死在头像目录内，
     * 不能借这个端点读存储里的其他对象；二来 {@code ..}、反斜杠这类路径把戏在正则这一层就进不来。
     */
    private static final Pattern KEY_PATTERN = Pattern.compile("avatar/(\\d{1,19})/([0-9a-f]{32})\\.(png|jpg|webp)");

    private final SysUserService userService;
    private final SysUserMapper userMapper;
    private final StorageProvider storage;
    private final CurrentUserProvider currentUser;

    public SysUserAvatarService(SysUserService userService, SysUserMapper userMapper, StorageProvider storage,
                                ObjectProvider<CurrentUserProvider> currentUser) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.storage = storage;
        this.currentUser = currentUser.getIfAvailable(() -> CurrentUserProvider.NOOP);
    }

    /** 读取端点返回的内容。 */
    public record AvatarContent(InputStream content, String contentType) {
    }

    /** 自助：给自己换头像，返回新的对象键。 */
    public String uploadOwn(Long userId, MultipartFile file) {
        SysUser self = userService.getOwnById(userId);
        if (self == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在: " + userId);
        }
        return replace(self, file, userId);
    }

    /**
     * 管理员：给别人换头像。
     *
     * <p>目标用户用会经过数据权限过滤的 {@code getById} 去查——查不到就是不在操作者的可见范围内，
     * 和通用编辑端点同一个口径。写库那一步才绕开数据权限。
     */
    public String upload(Long userId, MultipartFile file) {
        return replace(visibleUser(userId), file, currentUser.currentUserId());
    }

    /** 自助：清空自己的头像。 */
    public void clearOwn(Long userId) {
        SysUser self = userService.getOwnById(userId);
        if (self == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在: " + userId);
        }
        clear(self, userId);
    }

    /** 管理员：清空别人的头像。可见范围的判定同 {@link #upload}。 */
    public void clear(Long userId) {
        clear(visibleUser(userId), currentUser.currentUserId());
    }

    /**
     * 按对象键读取头像。键不合法、或对象不存在时返回空，由端点回 404。
     */
    public Optional<AvatarContent> open(String key) {
        Matcher m = key == null ? null : KEY_PATTERN.matcher(key);
        if (m == null || !m.matches()) {
            return Optional.empty();
        }
        String contentType = AvatarImage.Format.ofExtension(m.group(3))
                .map(AvatarImage.Format::contentType)
                .orElse(null);
        if (contentType == null) {
            return Optional.empty();
        }
        return storage.get(key).map(in -> new AvatarContent(in, contentType));
    }

    private SysUser visibleUser(Long userId) {
        SysUser user = userId == null ? null : userService.getById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在: " + userId);
        }
        return user;
    }

    /**
     * 先写新对象、再更新库、最后删旧对象。
     *
     * <p>顺序不能反过来：如果先删旧对象，而写库失败了，这个用户的头像就断了，库里指向一个已经不存在的键。
     * 现在这个顺序下最坏的情况是旧对象没删掉，留下一个没有任何记录指向的孤儿文件，这是可以接受的。
     */
    private String replace(SysUser user, MultipartFile file, Long operatorId) {
        byte[] bytes = readBounded(file);
        AvatarImage.Format format = AvatarImage.inspect(bytes);

        String key = "avatar/" + user.getId() + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + format.extension();
        storage.put(key, new ByteArrayInputStream(bytes), bytes.length, format.contentType());

        if (userMapper.updateAvatar(user.getId(), key, operatorId) == 0) {
            // 用户在这期间被删了：刚写的对象没人引用，顺手清掉
            removeQuietly(key);
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在: " + user.getId());
        }
        removeQuietly(user.getAvatar());
        return key;
    }

    private void clear(SysUser user, Long operatorId) {
        if (user.getAvatar() == null) {
            return;
        }
        userMapper.clearAvatar(user.getId(), operatorId);
        removeQuietly(user.getAvatar());
    }

    private static byte[] readBounded(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择头像图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BizException(ResultCode.BAD_REQUEST, "头像不能超过 1MB");
        }
        try {
            byte[] bytes = file.getBytes();
            // getSize() 由容器给出，这里以实际读到的字节数再判一次
            if (bytes.length > MAX_BYTES) {
                throw new BizException(ResultCode.BAD_REQUEST, "头像不能超过 1MB");
            }
            return bytes;
        } catch (IOException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "读取上传文件失败");
        }
    }

    /**
     * 删旧对象。失败只记日志：库已经指向新对象，旧文件删不掉只是多占一点空间，
     * 不该让用户看到"换头像失败"。
     */
    private void removeQuietly(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            return;
        }
        try {
            storage.remove(key);
        } catch (RuntimeException e) {
            log.warn("删除旧头像失败，留下孤儿对象: key={}", key, e);
        }
    }
}
