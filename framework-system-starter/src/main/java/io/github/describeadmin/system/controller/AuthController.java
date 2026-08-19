package io.github.describeadmin.system.controller;

import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.security.api.AuthRequest;
import io.github.describeadmin.security.api.LoginUser;
import io.github.describeadmin.security.core.AuthProviderRegistry;
import io.github.describeadmin.system.entity.SysMenu;
import io.github.describeadmin.system.service.SysMenuService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    public AuthController(AuthProviderRegistry registry, SysMenuService menuService) {
        this.registry = registry;
        this.menuService = menuService;
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
    public Result<LoginUser> login(@RequestBody Map<String, Object> body) {
        String type = String.valueOf(body.getOrDefault("type", "password"));
        return Result.ok(registry.authenticate(new AuthRequest(type, body)));
    }

    /** 指定用户的菜单树，供前端生成动态路由。 */
    @GetMapping("/menus")
    public Result<List<SysMenu>> menus(@RequestParam Long userId) {
        return Result.ok(menuService.treeOf(userId));
    }
}
