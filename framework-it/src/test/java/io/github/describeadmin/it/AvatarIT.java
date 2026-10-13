package io.github.describeadmin.it;

import io.github.describeadmin.common.api.DataScopeType;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.storage.api.StorageProvider;
import io.github.describeadmin.system.entity.SysDept;
import io.github.describeadmin.system.entity.SysRole;
import io.github.describeadmin.system.entity.SysUser;
import io.github.describeadmin.system.service.SysDeptService;
import io.github.describeadmin.system.service.SysMenuService;
import io.github.describeadmin.system.service.SysRoleService;
import io.github.describeadmin.system.service.SysUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户头像的 HTTP 端到端验证：自助上传、管理员代设、免登录读取、数据权限、通用编辑端点写不进去。
 *
 * <p>读回的头像一律比对<b>完整字节</b>，不只看状态码（CLAUDE.md 3.6）：
 * 端点返回 200 但内容是别人的图、或被截断的图，只看状态码是查不出来的。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("用户头像（HTTP 端到端）")
class AvatarIT extends AbstractMySqlIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired SysUserService userService;
    @Autowired SysDeptService deptService;
    @Autowired SysRoleService roleService;
    @Autowired SysMenuService menuService;
    @Autowired StorageProvider storage;

    private static final String PASSWORD = "pwd-12345";

    @Test
    @DisplayName("自助上传：/me 实时带出对象键，匿名可按字节读回；再换一次，旧对象被删")
    void selfUploadThenReplace() throws IOException {
        Long userId = createUser("avatar-self", null, null);
        String token = login("avatar-self");

        byte[] first = png(Color.RED);
        String firstKey = uploadOk("/api/auth/avatar", token, first, "a.png");
        assertThat(firstKey).matches("avatar/" + userId + "/[0-9a-f]{32}\\.png");
        assertThat(profileOf(token).get("avatar")).isEqualTo(firstKey);

        ResponseEntity<byte[]> img = rest.getForEntity("/api/system/" + firstKey, byte[].class);
        assertThat(img.getStatusCode()).as("不带令牌也能读").isEqualTo(HttpStatus.OK);
        assertThat(img.getBody()).isEqualTo(first);
        assertThat(img.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(img.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(img.getHeaders().getCacheControl()).contains("immutable");

        byte[] second = png(Color.BLUE);
        String secondKey = uploadOk("/api/auth/avatar", token, second, "b.png");
        assertThat(secondKey).isNotEqualTo(firstKey);
        assertThat(rest.getForEntity("/api/system/" + secondKey, byte[].class).getBody()).isEqualTo(second);
        assertThat(rest.getForEntity("/api/system/" + firstKey, byte[].class).getStatusCode())
                .as("旧头像对象应已删除").isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(storage.exists(firstKey)).isFalse();

        assertThat(code(rest.exchange("/api/auth/avatar", HttpMethod.DELETE,
                new HttpEntity<>(bearer(token)), Map.class))).isZero();
        assertThat(profileOf(token).get("avatar")).isNull();
        assertThat(storage.exists(secondKey)).isFalse();
    }

    @Test
    @DisplayName("/me 的 profile 实时读库：改了姓名、邮箱，不重新登录也能拿到新值")
    void meProfileIsLive() {
        createUser("avatar-live", null, null);
        String token = login("avatar-live");

        ResponseEntity<Map> put = rest.exchange("/api/auth/profile", HttpMethod.PUT,
                new HttpEntity<>(Map.of("nickname", "实时昵称", "email", "avatar-live@example.com"),
                        bearer(token)), Map.class);
        assertThat(code(put)).isZero();

        Map<String, Object> me = data(rest.exchange("/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), Map.class));
        assertThat(me.get("username")).as("LoginUser 的字段仍平铺在顶层").isEqualTo("avatar-live");
        assertThat(me.get("nickname")).as("顶层 nickname 是登录时的快照").isEqualTo("avatar-live");
        Map<?, ?> profile = (Map<?, ?>) me.get("profile");
        assertThat(profile.get("nickname")).isEqualTo("实时昵称");
        assertThat(profile.get("email")).isEqualTo("avatar-live@example.com");
    }

    @Test
    @DisplayName("SVG、伪装成图片的文本一律拒收，库里不留任何值")
    void rejectsNonImages() {
        createUser("avatar-svg", null, null);
        String token = login("avatar-svg");

        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        assertThat(code(upload("/api/auth/avatar", token, svg, "x.svg"))).isEqualTo(ResultCode.BAD_REQUEST.getCode());
        assertThat(code(upload("/api/auth/avatar", token, "<html></html>".getBytes(StandardCharsets.UTF_8), "x.png")))
                .isEqualTo(ResultCode.BAD_REQUEST.getCode());
        assertThat(profileOf(token).get("avatar")).isNull();
    }

    @Test
    @DisplayName("未登录不能上传")
    void uploadRequiresLogin() throws IOException {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", named(png(Color.GREEN), "a.png"));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map> resp = rest.exchange("/api/auth/avatar", HttpMethod.POST,
                new HttpEntity<>(form, headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("读取端点只认头像目录下形状合规的键，存储里的其他对象读不到")
    void readEndpointOnlyServesAvatarKeys() {
        storage.put("avatar/1/secret.txt", new ByteArrayInputStream("secret".getBytes(StandardCharsets.UTF_8)),
                6, "text/plain");
        storage.put("private/1/0123456789abcdef0123456789abcdef.png",
                new ByteArrayInputStream("secret".getBytes(StandardCharsets.UTF_8)), 6, "image/png");

        assertThat(rest.getForEntity("/api/system/avatar/1/secret.txt", byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/api/system/avatar/1/0123456789abcdef0123456789abcdef.png", byte[].class)
                .getStatusCode()).as("键合规但对象不存在").isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<byte[]> traversal = rest.getForEntity(
                "/api/system/avatar/..%2Fprivate%2F1/0123456789abcdef0123456789abcdef.png", byte[].class);
        assertThat(traversal.getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(traversal.getBody()).isNotEqualTo("secret".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("管理员代设头像：不递增版本号，换完头像再提交编辑表单不会撞乐观锁；通用编辑写不进 avatar")
    void adminSetsAvatarAndGenericUpdateCannotOverwrite() throws IOException {
        Long userId = createUser("avatar-target", null, null);
        String admin = login("admin", devSeedAdminPassword());

        Map<String, Object> before = data(rest.exchange("/api/system/user/" + userId, HttpMethod.GET,
                new HttpEntity<>(bearer(admin)), Map.class));

        byte[] content = png(Color.ORANGE);
        String key = uploadOk("/api/system/user/" + userId + "/avatar", admin, content, "a.png");
        assertThat(key).startsWith("avatar/" + userId + "/");

        // 编辑弹窗里拿的还是换头像之前的版本号，并且试图顺手把 avatar 改成外链
        Map<String, Object> body = new HashMap<>(before);
        body.put("nickname", "改过的昵称");
        body.put("avatar", "https://evil.example.com/x.png");
        ResponseEntity<Map> put = rest.exchange("/api/system/user/" + userId, HttpMethod.PUT,
                new HttpEntity<>(body, bearer(admin)), Map.class);
        assertThat(code(put)).as("头像写入不应递增版本号").isZero();

        SysUser after = userService.getOwnById(userId);
        assertThat(after.getNickname()).isEqualTo("改过的昵称");
        assertThat(after.getAvatar()).as("通用编辑端点不能写 avatar").isEqualTo(key);
        assertThat(rest.getForEntity("/api/system/" + key, byte[].class).getBody()).isEqualTo(content);

        assertThat(code(rest.exchange("/api/system/user/" + userId + "/avatar", HttpMethod.DELETE,
                new HttpEntity<>(bearer(admin)), Map.class))).isZero();
        assertThat(userService.getOwnById(userId).getAvatar()).isNull();
        assertThat(storage.exists(key)).isFalse();
    }

    @Test
    @DisplayName("管理员只能给自己数据权限范围内的用户设头像")
    void adminRespectsDataScope() throws IOException {
        Long deptA = createDept("avatar-dept-A");
        Long deptB = createDept("avatar-dept-B");
        Long roleId = createRole("ROLE_AVATAR_DEPT", DataScopeType.DEPT,
                List.of(menuIdOf("system:user:list"), menuIdOf("system:user:edit")));
        createUser("avatar-dept-admin", deptA, roleId);
        Long peer = createUser("avatar-dept-peer", deptA, null);
        Long outsider = createUser("avatar-dept-outsider", deptB, null);

        String token = login("avatar-dept-admin");
        uploadOk("/api/system/user/" + peer + "/avatar", token, png(Color.CYAN), "a.png");

        ResponseEntity<Map> denied = upload("/api/system/user/" + outsider + "/avatar", token,
                png(Color.CYAN), "a.png");
        assertThat(code(denied)).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(userService.getOwnById(outsider).getAvatar()).isNull();
    }

    @Test
    @DisplayName("没有 system:user:edit 的角色调代设端点被 403")
    void adminEndpointRequiresEditPermission() throws IOException {
        Long roleId = createRole("ROLE_AVATAR_LIST_ONLY", DataScopeType.ALL, List.of(menuIdOf("system:user:list")));
        createUser("avatar-list-only", null, roleId);
        Long target = createUser("avatar-list-target", null, null);

        ResponseEntity<Map> resp = upload("/api/system/user/" + target + "/avatar", login("avatar-list-only"),
                png(Color.PINK), "a.png");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(userService.getOwnById(target).getAvatar()).isNull();
    }

    // ---------------------------------------------------------------- helpers

    private String uploadOk(String url, String token, byte[] content, String filename) {
        ResponseEntity<Map> resp = upload(url, token, content, filename);
        assertThat(code(resp)).as("上传应成功: " + resp.getBody()).isZero();
        return String.valueOf(resp.getBody().get("data"));
    }

    private ResponseEntity<Map> upload(String url, String token, byte[] content, String filename) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", named(content, filename));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(form, headers), Map.class);
    }

    private static ByteArrayResource named(byte[] content, String filename) {
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private Map<?, ?> profileOf(String token) {
        Map<String, Object> me = data(rest.exchange("/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), Map.class));
        return (Map<?, ?>) me.get("profile");
    }

    private static byte[] png(Color color) throws IOException {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                img.setRGB(x, y, color.getRGB());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private Long createDept(String name) {
        SysDept dept = new SysDept();
        dept.setDeptName(name);
        dept.setParentId(0L);
        dept.setSort(0);
        dept.setStatus(1);
        return deptService.createDept(dept).getId();
    }

    private Long createRole(String code, DataScopeType scope, List<Long> menuIds) {
        SysRole role = new SysRole();
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setSort(0);
        role.setDataScope(scope.getCode());
        roleService.save(role);
        roleService.assignMenus(role.getId(), menuIds);
        return role.getId();
    }

    private Long createUser(String username, Long deptId, Long roleId) {
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setNickname(username);
        u.setDeptId(deptId);
        SysUser created = userService.createUser(u, PASSWORD, roleId == null ? List.of() : List.of(roleId));
        clearPwdResetRequired(username);
        return created.getId();
    }

    private Long menuIdOf(String permCode) {
        return menuService.list().stream()
                .filter(m -> permCode.equals(m.getPermCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("种子数据缺少权限点: " + permCode))
                .getId();
    }

    private String login(String username) {
        return login(username, PASSWORD);
    }

    private String login(String username, String password) {
        ResponseEntity<Map> resp = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("type", "password", "username", username, "password", password),
                        jsonHeaders()), Map.class);
        assertThat(resp.getStatusCode()).as("登录应成功: " + username).isEqualTo(HttpStatus.OK);
        return String.valueOf(((Map<?, ?>) resp.getBody().get("data")).get("token"));
    }

    private static int code(ResponseEntity<Map> resp) {
        assertThat(resp.getStatusCode()).as("业务错误也应是 HTTP 200: " + resp.getBody()).isEqualTo(HttpStatus.OK);
        return ((Number) resp.getBody().get("code")).intValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ResponseEntity<Map> resp) {
        assertThat(code(resp)).as(String.valueOf(resp.getBody())).isZero();
        return (Map<String, Object>) resp.getBody().get("data");
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        return headers;
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
