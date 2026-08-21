package io.github.describeadmin.system.controller;

import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.security.api.ActiveSession;
import io.github.describeadmin.security.api.TokenStore;
import io.github.describeadmin.system.core.OperLog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 在线用户与强制下线。
 *
 * <p>数据直接来自 {@link TokenStore}，<b>没有对应的数据库表</b>——会话状态本来就存在
 * 令牌存储里，再落一张表就会出现两份状态需要同步，而它们必然会不一致。
 * 因此本类不继承 {@code BaseController}，权限点用 {@code @PreAuthorize} 显式声明。
 *
 * <p><b>默认实现下的可见范围</b>：框架默认的 {@code InMemoryTokenStore} 只持有<b>本实例</b>
 * 的会话。多实例部署时这个页面只能看到当前实例的在线用户，踢下线也只对当前实例生效。
 * 需要全局视图请换用集中式的 {@code TokenStore} 实现，上层代码不用动。
 */
@RestController
@RequestMapping("/api/system/online")
public class SysOnlineController {

    private final TokenStore tokenStore;

    public SysOnlineController(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    /**
     * 在线会话列表。
     *
     * <p>会话粒度而非用户粒度：同一个用户多设备登录会出现多条。
     * 响应里不含令牌本身，理由见 {@link ActiveSession} 的类注释。
     */
    @PreAuthorize("hasAuthority('system:online:list')")
    @GetMapping
    public Result<List<ActiveSession>> list() {
        return Result.ok(tokenStore.listActive());
    }

    /**
     * 强制某用户下线，吊销其全部令牌。
     *
     * <p>按用户而非按会话吊销：一个还在被冒用的账号，只踢掉其中一个设备没有意义。
     *
     * <p>不禁止管理员踢自己——那只是把自己登出，重新登录即可，
     * 为它加一个特例反而要求调用方理解一条额外规则。
     *
     * @return 实际吊销的令牌数；该用户本来就不在线时为 0
     */
    @OperLog(module = "system:online", description = "强制下线")
    @PreAuthorize("hasAuthority('system:online:remove')")
    @DeleteMapping("/{userId}")
    public Result<Integer> forceLogout(@PathVariable Long userId) {
        return Result.ok(tokenStore.revokeAllOf(userId));
    }
}
