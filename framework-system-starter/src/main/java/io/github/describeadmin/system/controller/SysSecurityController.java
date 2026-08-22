package io.github.describeadmin.system.controller;

import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.security.core.LoginAttemptGuard;
import io.github.describeadmin.system.core.OperLog;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 登录锁定的管理侧可观测性（docs/LOGIN_MODULE_AUDIT.md D 项）。
 *
 * <p>数据直接来自 {@link LoginAttemptGuard}，没有对应的数据库表——理由与
 * {@link SysOnlineController} 一致：锁定状态本来就存在 {@code CacheProvider} 里，
 * 再落一张表会出现两份状态需要同步。因此本类同样不继承 {@code BaseController}，
 * 权限点用 {@code @PreAuthorize} 显式声明。
 *
 * <p><b>必须用 {@code ObjectProvider} 而不是直接注入或 {@code @ConditionalOnBean}</b>：
 * {@code FrameworkSystemAutoConfiguration} 声明了
 * {@code @AutoConfiguration(before = FrameworkSecurityAutoConfiguration.class)}——
 * system 先于 security 被装配，此时 {@link LoginAttemptGuard} 这个 Bean 还不存在。
 * 若给本类或本类的方法加 {@code @ConditionalOnBean(LoginAttemptGuard.class)}，
 * 条件求值会因为"看不到还没注册的 Bean"而失败，导致本控制器被静默跳过——
 * 引了却没生效，启动毫无异常，与 docs/registry.md 准入规范第 3 条描述的是同一类陷阱，
 * 只是这次发生在核心内部两个 starter 之间。用 {@code ObjectProvider} 规避：
 * 控制器本身始终注册，只在真正处理请求时才判断 Guard 是否存在——
 * {@code describeadmin.security.lockout.enabled=false} 时接口返回空列表/空操作，
 * 而不是报错或者干脆消失不见。
 */
@RestController
@RequestMapping("/api/system/security")
public class SysSecurityController {

    private final ObjectProvider<LoginAttemptGuard> attemptGuard;

    public SysSecurityController(ObjectProvider<LoginAttemptGuard> attemptGuard) {
        this.attemptGuard = attemptGuard;
    }

    /**
     * 当前处于登录锁定状态的用户名列表。
     *
     * <p>锁定按设计到点自动解锁，不需要管理员介入（见 {@link LoginAttemptGuard} 类注释）；
     * 本接口只是给"误锁"场景提供一个可观测的窗口。
     */
    @PreAuthorize("hasAuthority('system:security:list')")
    @GetMapping("/locked-accounts")
    public Result<List<String>> lockedAccounts() {
        LoginAttemptGuard guard = attemptGuard.getIfAvailable();
        return Result.ok(guard == null ? List.of() : new ArrayList<>(guard.listLockedUsernames()));
    }

    /**
     * 手动解锁：清除某用户名的失败计数，效果等同于提前到点。
     *
     * <p>{@code lockout.enabled=false} 时本来就不存在锁定状态，本操作是安全的空操作。
     */
    @OperLog(module = "system:security", description = "解锁账号")
    @PreAuthorize("hasAuthority('system:security:unlock')")
    @DeleteMapping("/locked-accounts/{username}")
    public Result<Void> unlock(@PathVariable String username) {
        LoginAttemptGuard guard = attemptGuard.getIfAvailable();
        if (guard != null) {
            guard.unlock(username);
        }
        return Result.ok();
    }
}
