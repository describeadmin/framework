package io.github.describeadmin.system.controller;

import io.github.describeadmin.system.service.SysUserAvatarService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 头像读取。<b>免登录</b>：这条路径由 {@code FrameworkSystemAutoConfiguration} 通过
 * {@code PermitAllContributor} 加进安全白名单。
 *
 * <p>为什么要免登录：令牌放在 {@code Authorization} 头里，{@code <img src>} 发出的请求不带这个头，
 * 顶栏、锁屏、用户列表里的每一处头像都会变成 401。对应的约束是：
 * <ul>
 *   <li>只能读头像目录下、形状完全合规的对象键，见 {@code SysUserAvatarService.open}。
 *       这个端点读不到存储里的其他东西</li>
 *   <li>键里有 128 位随机段，拿不到键就枚举不出来；能拿到键的只有能看到这个用户的人</li>
 *   <li>{@code nosniff}：格式在上传时已经按魔数校验过，再禁止浏览器自己嗅探内容类型，作为双保险</li>
 * </ul>
 *
 * <p>直接返回二进制，不包 {@code Result}——这是给 {@code <img>} 用的。
 */
@RestController
@RequestMapping("/api/system/avatar")
public class SysAvatarController {

    /** 每次上传都换新键，同一个键的内容永远不变，所以可以让浏览器永久缓存。 */
    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final SysUserAvatarService avatarService;

    public SysAvatarController(SysUserAvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @GetMapping("/{userId}/{file}")
    public ResponseEntity<InputStreamResource> get(@PathVariable String userId, @PathVariable String file) {
        return avatarService.open("avatar/" + userId + "/" + file)
                .map(c -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(c.contentType()))
                        .cacheControl(CACHE)
                        .header("X-Content-Type-Options", "nosniff")
                        .body(new InputStreamResource(c.content())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
