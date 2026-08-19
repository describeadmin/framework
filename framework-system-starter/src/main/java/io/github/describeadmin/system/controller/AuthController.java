package io.github.describeadmin.system.controller;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.security.api.AuthRequest;
import io.github.describeadmin.security.api.LoginResult;
import io.github.describeadmin.security.api.LoginUser;
import io.github.describeadmin.security.api.TokenStore;
import io.github.describeadmin.security.autoconfigure.FrameworkSecurityProperties;
import io.github.describeadmin.security.core.AuthProviderRegistry;
import io.github.describeadmin.security.core.TokenAuthenticationFilter;
import io.github.describeadmin.system.entity.SysMenu;
import io.github.describeadmin.system.service.SysMenuService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 认证与当前用户上下文接口。
 *
 * <p>由框架提供，业务方无需实现——这正是把系统管理收进框架的意义：
 * 框架修了登录逻辑的问题，业务方升个版本就拿到了。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthProviderRegistry registry;
    private final SysMenuService menuService;
    private final TokenStore tokenStore;
    private final FrameworkSecurityProperties securityProperties;

    public AuthController(AuthProviderRegistry registry,
                          SysMenuService menuService,
                          TokenStore tokenStore,
                          FrameworkSecurityProperties securityProperties) {
        this.registry = registry;
        this.menuService = menuService;
        this.tokenStore = tokenStore;
        this.securityProperties = securityProperties;
    }

    /**
     * 当前项目启用了哪些登录方式。
     *
     * <p>前端登录页调用本接口动态渲染，<b>不要把登录按钮硬编码在页面里</b>——
     * 这是插件化在前端侧成立的关键（develop_plan.md 3.2）。
     * 引入浙政钉插件后这里自动多出一项，前后端都不用改代码。
     */
    @GetMapping("/providers")
    public Result<List<String>> providers() {
        return Result.ok(registry.availableTypes());
    }

    /**
     * 登录。
     *
     * <p>除 type 外的字段整体透传给对应 AuthProvider，
     * 因此新增登录方式不需要改本接口的签名。
     */
    @PostMapping("/login")
    public Result<LoginResult> login(@RequestBody Map<String, Object> body) {
        String type = String.valueOf(body.getOrDefault("type", "password"));
        LoginUser user = registry.authenticate(new AuthRequest(type, body));
        String token = tokenStore.issue(user);
        return Result.ok(new LoginResult(
                token, securityProperties.getTokenTtl().toSeconds(), user));
    }

    /**
     * 登出：吊销当前令牌。
     *
     * <p>吊销的是<b>请求头里带的那一个</b>令牌，而不是该用户的全部令牌——
     * 用户在手机上登出不应该把电脑上的会话也踢掉。
     * 需要「全端下线」语义时用 {@code TokenStore.revokeAllOf(userId)}。
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = TokenAuthenticationFilter.extractToken(request);
        if (token != null) {
            tokenStore.revoke(token);
        }
        SecurityContextHolder.clearContext();
        return Result.ok();
    }

    /**
     * 当前登录用户。
     *
     * <p>前端刷新页面后用它恢复用户信息与权限，不必把这些持久化在浏览器里——
     * 存在前端的权限集合是不可信的，每次都回源才是对的。
     */
    @GetMapping("/me")
    public Result<LoginUser> me() {
        return Result.ok(currentUser());
    }

    /**
     * 当前登录用户的菜单树，供前端生成动态路由。
     *
     * <p>用户 ID 取自登录态而非请求参数——之前的实现让调用方传 {@code userId}，
     * 那等于任何登录用户都能拿到别人的菜单树，是一个越权读取。
     */
    @GetMapping("/menus")
    public Result<List<SysMenu>> menus() {
        return Result.ok(menuService.treeOf(currentUser().getUserId()));
    }

    private LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser user)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return user;
    }
}
