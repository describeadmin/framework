package io.github.describeadmin.system.controller;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.security.api.AuthRequest;
import io.github.describeadmin.security.api.IssuedTokens;
import io.github.describeadmin.security.api.LoginResult;
import io.github.describeadmin.security.api.LoginUser;
import io.github.describeadmin.security.api.TokenStore;
import io.github.describeadmin.security.autoconfigure.FrameworkSecurityProperties;
import io.github.describeadmin.security.core.AuthProviderRegistry;
import io.github.describeadmin.security.core.TokenAuthenticationFilter;
import io.github.describeadmin.system.entity.SysMenu;
import io.github.describeadmin.system.service.SysMenuService;
import io.github.describeadmin.system.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final SysUserService userService;

    public AuthController(AuthProviderRegistry registry,
                          SysMenuService menuService,
                          TokenStore tokenStore,
                          FrameworkSecurityProperties securityProperties,
                          SysUserService userService) {
        this.registry = registry;
        this.menuService = menuService;
        this.tokenStore = tokenStore;
        this.securityProperties = securityProperties;
        this.userService = userService;
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
     *
     * <p>是否签发 refresh token 由 {@code describeadmin.security.refresh-token.enabled} 控制
     * （默认开启）。关闭时退回只签发 access token，{@code LoginResult.refreshToken} 为 null，
     * 前端应据此不再调用 {@link #refresh(Map)}。
     */
    @PostMapping("/login")
    public Result<LoginResult> login(@RequestBody Map<String, Object> body) {
        String type = String.valueOf(body.getOrDefault("type", "password"));
        LoginUser user = registry.authenticate(new AuthRequest(type, body));
        IssuedTokens tokens = securityProperties.getRefreshToken().isEnabled()
                ? tokenStore.issueWithRefresh(user)
                : new IssuedTokens(tokenStore.issue(user), null);
        return Result.ok(new LoginResult(
                tokens.getAccessToken(), tokens.getRefreshToken(),
                securityProperties.getTokenTtl().toSeconds(),
                tokens.getRefreshToken() == null ? 0 : securityProperties.getRefreshToken().getTtl().toSeconds(),
                user));
    }

    /**
     * 用 refresh token 换发新的一对令牌。
     *
     * <p><b>刻意不挂 {@code @PreAuthorize}、不要求已登录</b>——本端点存在的意义就是
     * "access token 已过期、调用方此刻还不是一个已认证的请求"，挂权限校验会自相矛盾。
     * 校验完全下沉到 {@link TokenStore#refresh(String)} 内部（是否存在、是否过期、
     * 是否已被使用/吊销）。需要加入 {@code permit-all}，见
     * {@code FrameworkSecurityAutoConfiguration.BUILT_IN_PERMIT_ALL}。
     *
     * <p>刷新只延长会话，不会重新拉取角色/权限——与"权限快照在登录时确定"是同一既有取舍，
     * 见 {@link TokenStore#refresh(String)} 的 javadoc。
     */
    @PostMapping("/refresh")
    public Result<LoginResult> refresh(@RequestBody Map<String, String> body) {
        IssuedTokens tokens = tokenStore.refresh(body.get("refreshToken"))
                .orElseThrow(() -> new BizException(ResultCode.UNAUTHORIZED, "刷新令牌无效或已过期，请重新登录"));
        LoginUser user = tokenStore.resolve(tokens.getAccessToken())
                .orElseThrow(() -> new BizException(ResultCode.UNAUTHORIZED, "刷新失败，请重新登录"));
        return Result.ok(new LoginResult(
                tokens.getAccessToken(), tokens.getRefreshToken(),
                securityProperties.getTokenTtl().toSeconds(),
                securityProperties.getRefreshToken().getTtl().toSeconds(),
                user));
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

    /**
     * 自助改密：当前登录用户修改自己的密码。
     *
     * <p>与 {@code SysUserController#resetPassword}（管理员改别人，需要 {@code system:user:edit}
     * 权限）是两条不同的路径——这里操作对象永远是"当前登录用户"，任何已登录账号都能调用，
     * 不需要挂具体权限点。校验旧密码、吊销全部令牌都收在
     * {@link SysUserService#changeOwnPassword(Long, String, String)} 一个事务方法里，
     * 避免调用方漏掉吊销这一步。
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody Map<String, String> body) {
        userService.changeOwnPassword(currentUser().getUserId(), body.get("oldPassword"), body.get("newPassword"));
        return Result.ok();
    }

    private LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser user)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return user;
    }
}
