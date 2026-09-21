package io.github.describeadmin.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 服务端权限点校验的 HTTP 端到端验证。
 *
 * <p><b>本组测试存在的理由</b>：在此之前，权限点只被下发给前端用于按钮显隐，
 * 服务端对所有非白名单接口一律只要求"已认证"。也就是说，任何一个能登录的账号
 * 直接构造 HTTP 请求就能调用任何接口——界面看起来是受控的，实际并不受控。
 * 这类缺陷从页面上永远看不出来，只能在 HTTP 层测。
 *
 * <p>覆盖两条独立的拒绝路径，它们的实现完全不同，必须分别验证：
 * <ul>
 *   <li>{@code BaseController} 通用端点 → {@code PermissionChecker.require}</li>
 *   <li>业务自定义端点 → Spring Security 的 {@code @PreAuthorize}</li>
 * </ul>
 * 两者都必须产出 403 + {@code code=40300}，而<b>不是 500</b>——
 * framework-web-starter 的 {@code GlobalExceptionHandler} 有一个
 * {@code @ExceptionHandler(Throwable.class)} 兜底，会把 {@code AccessDeniedException}
 * 吞成"服务器内部错误"。那种情况下权限其实拦住了，但前端看到的是 500，
 * 排查方向会完全跑偏。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("权限点校验（HTTP 端到端）")
class PermissionEnforcementIT extends AbstractMySqlIntegrationTest {

    @Autowired TestRestTemplate rest;

    // ------------------------------------------------------- 无权限账号被拒

    @Test
    @DisplayName("无角色账号访问通用列表端点被拒，且是 403 而不是 500")
    void noRoleUserIsDeniedOnBaseControllerEndpoint() {
        String token = tokenOfPowerlessUser("nobody-list");

        ResponseEntity<String> resp = rest.exchange("/api/system/user", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), String.class);

        // 三项分开断言：任何一项单独成立都不足以说明行为正确
        assertThat(resp.getStatusCode())
                .as("必须是 403。若为 500，说明 AccessDeniedException 被 Throwable 兜底吃掉了")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody())
                .as("响应体的业务码应为 40300")
                .contains("\"code\":40300");
        assertThat(resp.getBody())
                .as("必须是 JSON，不能是 Spring Security 默认的 HTML 错误页")
                .doesNotContain("<html");
    }

    @Test
    @DisplayName("无角色账号访问 @PreAuthorize 端点被拒，与通用端点返回同一份响应")
    void noRoleUserIsDeniedOnPreAuthorizeEndpoint() {
        String token = tokenOfPowerlessUser("nobody-tree");

        ResponseEntity<String> viaPreAuthorize = rest.exchange("/api/system/menu/tree",
                HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
        ResponseEntity<String> viaChecker = rest.exchange("/api/system/menu",
                HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);

        assertThat(viaPreAuthorize.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(viaChecker.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // 两条路径的实现不同，但对客户端必须不可区分——否则前端要写两套错误处理。
        // timestamp 每次请求都不同，比对前剔除。
        assertThat(withoutTimestamp(viaPreAuthorize.getBody()))
                .isEqualTo(withoutTimestamp(viaChecker.getBody()));
        // 值断言：错误消息里的中文不能是乱码（CLAUDE.md 3.6）
        assertThat(viaPreAuthorize.getBody()).contains("无权访问");
    }

    @Test
    @DisplayName("无权限的写操作被拒，且数据库里确实没有落数据")
    void deniedWriteLeavesNoTrace() {
        String token = tokenOfPowerlessUser("nobody-write");

        ResponseEntity<String> resp = rest.exchange("/api/system/dept", HttpMethod.POST,
                new HttpEntity<>(Map.of("deptName", "越权创建的部门", "parentId", 0, "sort", 1),
                        bearer(token)),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // 只断言状态码不够：要确认拦截发生在写库之前，而不是写完才报错
        ResponseEntity<Map> asAdmin = rest.exchange("/api/system/dept/tree", HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOfAdmin())), Map.class);
        assertThat(String.valueOf(asAdmin.getBody().get("data")))
                .as("被拒的创建不应在库里留下任何痕迹")
                .doesNotContain("越权创建的部门");
    }

    // --------------------------------------------------------- 有权限则放行

    @Test
    @DisplayName("ADMIN 拥有全部权限点，通用端点与 @PreAuthorize 端点都放行")
    void adminPassesBothPaths() {
        String token = tokenOfAdmin();

        assertThat(rest.exchange("/api/system/user", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.exchange("/api/system/menu/tree", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("权限点前缀由 @RequestMapping 推导，与种子数据里登记的权限点对得上")
    void permPrefixMatchesSeededPermissionCodes() {
        String token = tokenOfAdmin();

        // 这四个端点走的是 BaseController 通用 list，权限点分别推导为
        // system:user / system:role / system:menu / system:dept 加 :list。
        // 只要推导规则与 seed-rbac.sql 不一致，ADMIN 也会被拒——
        // 这条用例正是用来抓"推导出的前缀没人授权"这种静默错配的。
        for (String path : List.of("/api/system/user", "/api/system/role",
                "/api/system/menu", "/api/system/dept")) {
            assertThat(rest.exchange(path, HttpMethod.GET,
                    new HttpEntity<>(bearer(token)), String.class).getStatusCode())
                    .as("推导出的权限点前缀与种子数据不匹配: " + path)
                    .isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    @DisplayName("业务模块的权限点前缀同样推导正确（/api/project → project:list）")
    void permPrefixWorksForGeneratedBusinessModule() {
        assertThat(rest.exchange("/api/project", HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOfAdmin())), String.class).getStatusCode())
                .as("codegen 产出的模块，其 menu-*.sql 已把 project:list 授给 ADMIN")
                .isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------- 跨模块引用数据（/options，CLAUDE.md 4.11）

    /**
     * A 档「引用元数据」端点只要求登录。
     *
     * <p>这一条是整套约定成立与否的关键：用户管理页要填「角色」「部门」两个选择器，
     * 过去只能去调 {@code /api/system/role} 与 {@code /api/system/dept/tree}，
     * 于是只授「用户管理」的角色必然 403，而补授对方的 {@code :list} 又会让侧边栏
     * 多出不该有的管理入口。这里用<b>一个角色都没有</b>的账号验证，
     * 比"只授 system:user:*"更强——连一个权限点都没有也应当拿得到。
     */
    @Test
    @DisplayName("无角色账号能取到 /options 引用数据——跨模块下拉不再需要对方的 list 权限")
    void optionEndpointsRequireOnlyLogin() {
        String token = tokenOfPowerlessUser("nobody-options");

        for (String path : List.of("/api/system/role/options",
                "/api/system/dept/options", "/api/system/menu/options")) {
            assertThat(rest.exchange(path, HttpMethod.GET,
                    new HttpEntity<>(bearer(token)), String.class).getStatusCode())
                    .as("仅登录即可访问: " + path)
                    .isEqualTo(HttpStatus.OK);
        }
    }

    /**
     * 只比对状态码不够——放行的同时必须确认下发的<b>确实是</b>最小形状。
     *
     * <p>若哪天有人图省事把 {@code listOptions()} 改成直接返回实体，测试照样全绿，
     * 而实际效果是把角色标识、部门负责人电话、全部权限点清单发给了每一个登录用户。
     * 所以这里既断言"该有的值在"（顺带覆盖 CLAUDE.md 3.6 的字符集口径），
     * 也断言"不该有的字段不在"。
     */
    @Test
    @DisplayName("/options 只下发 id + 显示名，不夹带模块内部字段")
    void optionEndpointsExposeOnlyMinimalShape() {
        HttpEntity<Void> req = new HttpEntity<>(bearer(tokenOfPowerlessUser("nobody-shape")));

        String roles = rest.exchange("/api/system/role/options", HttpMethod.GET, req, String.class).getBody();
        assertThat(roles).contains("\"label\":\"超级管理员\"");
        // 断言的是**字段名**不存在，而不是某个取值不出现在正文里：整个容器由全部 IT 共用，
        // 别的用例建的角色名里就含 "ADMIN"（DataScopePermissionIT 的 ROLE_SCOPE_ALL_NOT_ADMIN），
        // 按取值断言会因为一个合法的 label 而误报
        assertThat(roles).as("roleCode / 数据权限档位 / 首页都是授权内部结构，不该下发")
                .doesNotContain("roleCode")
                .doesNotContain("dataScope")
                .doesNotContain("homePath");

        String depts = rest.exchange("/api/system/dept/options", HttpMethod.GET, req, String.class).getBody();
        assertThat(depts).contains("\"label\":\"总部\"");
        assertThat(depts).as("负责人/电话/物化路径都超出了\"填个下拉框\"该有的范围")
                .doesNotContain("leader").doesNotContain("phone").doesNotContain("ancestors");

        String menus = rest.exchange("/api/system/menu/options", HttpMethod.GET, req, String.class).getBody();
        assertThat(menus).contains("\"label\":\"用户管理\"");
        assertThat(menus).as("把权限点清单发给每个登录用户，等于公开系统有哪些受控操作")
                .doesNotContain("permCode").doesNotContain("system:user:add");
    }

    /**
     * A 档放行不等于变相放权——同一个账号对真正的管理端点必须仍然被拒。
     *
     * <p>没有这一条，「只要求登录」就可能被实现成「把这些模块整体放开」而无人察觉。
     */
    @Test
    @DisplayName("能取 /options 的账号，对应模块的管理端点仍然 403")
    void optionEndpointsDoNotWidenAccess() {
        String token = tokenOfPowerlessUser("nobody-still-denied");

        for (String path : List.of("/api/system/role", "/api/system/dept/tree",
                "/api/system/menu/tree")) {
            assertThat(rest.exchange(path, HttpMethod.GET,
                    new HttpEntity<>(bearer(token)), String.class).getStatusCode())
                    .as("/options 放行不应波及管理端点: " + path)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    /**
     * 「仅登录」的底座是过滤链的 {@code anyRequest().authenticated()}——
     * 这些端点<b>没有</b>写进 {@code BUILT_IN_PERMIT_ALL}，所以未登录必须拿不到。
     *
     * <p>不写这条的话，将来有人"为了方便"把 {@code /options} 加进
     * {@code describeadmin.security.permit-all}，就成了不需要登录的全量组织架构导出接口。
     */
    @Test
    @DisplayName("未登录访问 /options 得到 401，不是匿名可读")
    void optionEndpointsRejectAnonymous() {
        for (String path : List.of("/api/system/role/options",
                "/api/system/dept/options", "/api/system/menu/options")) {
            assertThat(rest.getForEntity(path, String.class).getStatusCode())
                    .as("未登录不应拿到任何引用数据: " + path)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * B 档「功能自带数据」按<b>宿主模块</b>的权限点校验。
     *
     * <p>「分配菜单」是角色管理自己的功能，它需要的完整菜单树由角色模块提供、
     * 按 {@code system:role:list} 校验——而不是去调菜单模块的 {@code /tree}
     * 再要求 {@code system:menu:list}。判据见 CLAUDE.md 4.11：
     * 通用 OptionItem 形状够用走 A 档，不够用（这里要 menuType/path）走 B 档。
     */
    @Test
    @DisplayName("/role/grantable-menus 按角色模块权限点校验，且含按钮节点")
    void grantableMenusIsGuardedByRolePermission() {
        assertThat(rest.exchange("/api/system/role/grantable-menus", HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOfPowerlessUser("nobody-grant"))), String.class)
                .getStatusCode())
                .as("无 system:role:list 应被拒")
                .isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> asAdmin = rest.exchange("/api/system/role/grantable-menus",
                HttpMethod.GET, new HttpEntity<>(bearer(tokenOfAdmin())), String.class);
        assertThat(asAdmin.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 授权树必须能勾到 BUTTON 级权限点，所以这棵树与 /menu/options 不同，是全量的
        assertThat(asAdmin.getBody())
                .as("授权树要含按钮节点，否则勾不到 add/edit/remove")
                .contains("system:user:add");
        assertThat(asAdmin.getBody())
                .as("「首页」选择器要靠 menuType/path 判断哪些节点可选")
                .contains("menuType").contains("path");
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 造一个能登录但没有任何角色的账号。
     *
     * <p>不复用 admin 再去摘权限，是因为 ADMIN 的授权关系是种子数据的一部分，
     * 改动它会污染同一个容器里的其他测试。
     */
    private String tokenOfPowerlessUser(String username) {
        ResponseEntity<Map> created = rest.exchange("/api/system/user/with-password",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "username", username,
                        "password", "pwd-12345",
                        "nickname", "无权限账号",
                        "status", 1), bearer(tokenOfAdmin())),
                Map.class);
        assertThat(created.getStatusCode()).as("前置条件：建号应成功").isEqualTo(HttpStatus.OK);
        // 管理员建号会置强制改密标记；本组测的是权限点校验，与强制改密无关，清掉它
        clearPwdResetRequired(username);

        ResponseEntity<Map> login = rest.postForEntity("/api/auth/login",
                json(Map.of("type", "password", "username", username, "password", "pwd-12345")),
                Map.class);
        assertThat(login.getStatusCode()).as("前置条件：新账号应能登录").isEqualTo(HttpStatus.OK);

        Map<?, ?> data = (Map<?, ?>) login.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) data.get("user");
        assertThat((List<?>) user.get("permissions"))
                .as("前置条件：该账号不应有任何权限点，否则本组测试失去意义")
                .isEmpty();
        return String.valueOf(data.get("token"));
    }

    private String tokenOfAdmin() {
        ResponseEntity<Map> resp = rest.postForEntity("/api/auth/login",
                json(Map.of("type", "password", "username", "admin", "password", devSeedAdminPassword())),
                Map.class);
        assertThat(resp.getStatusCode()).as("登录应成功，检查种子数据").isEqualTo(HttpStatus.OK);
        return String.valueOf(((Map<?, ?>) resp.getBody().get("data")).get("token"));
    }

    /**
     * 剔除响应体里每次都不同的 timestamp 字段，便于比对两条拒绝路径的产出。
     *
     * <p>{@code Result.timestamp} 是 long，按框架的全局约定（CLAUDE.md 4.8）序列化为字符串
     * （即 {@code "timestamp":"171..."} 而不是 {@code "timestamp":171...}），
     * 值两侧要各匹配一个可选的引号，否则正则匹配不上、timestamp 原样留在字符串里，
     * 两次请求的 timestamp 几乎必然不同，导致本来该稳定通过的比对偶发失败。
     */
    private static String withoutTimestamp(String body) {
        return body == null ? null : body.replaceAll("\"timestamp\":\"?\\d+\"?,?", "");
    }

    private static HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        return new HttpEntity<>(body, headers);
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        headers.setBearerAuth(token);
        return headers;
    }
}
