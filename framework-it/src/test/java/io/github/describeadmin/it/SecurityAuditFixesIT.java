package io.github.describeadmin.it;

import io.github.describeadmin.common.api.DataScopeType;
import io.github.describeadmin.system.entity.SysDept;
import io.github.describeadmin.system.entity.SysMenu;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1.0.0 发布前审计报告里那几处缺陷的回归（审计报告-1.0.0-发布前.md S1/S2/S8/S9 与低风险 2）。
 *
 * <p>业务异常走 HTTP 200 + 响应体 {@code code}（见 {@code GlobalExceptionHandler}），
 * 所以这里断言的是 {@code code} 与<b>库里的具体值</b>，不只看"请求失败了"——
 * 拒绝了但数据已经被改掉，和真的没改是两回事。
 *
 * <p>与其余 IT 共享容器、不回滚，用例各自起独立前缀的夹具。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("审计修复回归（越权 / 删除 / 菜单成环 / 审计字段 / 删除引用校验）")
class SecurityAuditFixesIT extends AbstractMySqlIntegrationTest {

    private static final String PASSWORD = "Pwd-12345";
    private static final int OK = 0;
    private static final int BAD_REQUEST = 40000;
    private static final int UNAUTHORIZED = 40100;
    private static final int FORBIDDEN = 40300;
    private static final int NOT_FOUND = 40400;

    @Autowired TestRestTemplate rest;
    @Autowired SysDeptService deptService;
    @Autowired SysRoleService roleService;
    @Autowired SysUserService userService;
    @Autowired SysMenuService menuService;
    @Autowired JdbcTemplate jdbc;

    // ------------------------------------------------------------ S1 越权 / 提权

    @Test
    @DisplayName("分配角色、查角色：目标用户不在数据权限范围内一律 404，角色不变")
    void assignRolesRequiresVisibleTarget() {
        Long deptA = createDept("saf-scope-A", 0L);
        Long deptB = createDept("saf-scope-B", 0L);
        Long operatorRole = createRole("SAF_SCOPE_OP", DataScopeType.DEPT,
                menuIds("system:user:list", "system:user:assign-role"));
        Long smallRole = createRole("SAF_SCOPE_SMALL", DataScopeType.SELF, menuIds("system:user:list"));
        createUser("saf-scope-op", deptA, List.of(operatorRole));
        Long outsider = createUser("saf-scope-outsider", deptB, List.of(smallRole));
        String token = login("saf-scope-op");

        assertThat(code(exchange(token, HttpMethod.PUT, "/api/system/user/" + outsider + "/roles", List.of())))
                .isEqualTo(NOT_FOUND);
        assertThat(code(exchange(token, HttpMethod.GET, "/api/system/user/" + outsider + "/roles", null)))
                .isEqualTo(NOT_FOUND);
        assertThat(userService.roleIdsOf(outsider)).containsExactly(smallRole);
    }

    @Test
    @DisplayName("授权上限：不能授出、也不能摘掉超出自身菜单的角色；建号带 ADMIN 同样被拒；上限内的照常成功")
    void roleGrantIsCappedByOperatorMenus() {
        Long operatorRole = createRole("SAF_CAP_OP", DataScopeType.ALL,
                menuIds("system:user:list", "system:user:add", "system:user:assign-role"));
        Long smallRole = createRole("SAF_CAP_SMALL", DataScopeType.SELF, menuIds("system:user:list"));
        createUser("saf-cap-op", null, List.of(operatorRole));
        Long peer = createUser("saf-cap-peer", null, List.of());
        Long adminHolder = createUser("saf-cap-admin-holder", null, List.of(adminRoleId()));
        String token = login("saf-cap-op");

        assertThat(code(exchange(token, HttpMethod.PUT, "/api/system/user/" + peer + "/roles",
                List.of(adminRoleId())))).isEqualTo(FORBIDDEN);
        assertThat(userService.roleIdsOf(peer)).isEmpty();

        assertThat(code(exchange(token, HttpMethod.PUT, "/api/system/user/" + adminHolder + "/roles",
                List.of()))).as("摘掉别人的 ADMIN 同样越权").isEqualTo(FORBIDDEN);
        assertThat(userService.roleIdsOf(adminHolder)).containsExactly(adminRoleId());

        Map<String, Object> body = new HashMap<>();
        body.put("username", "saf-cap-new-admin");
        body.put("password", PASSWORD);
        body.put("roleIds", List.of(String.valueOf(adminRoleId())));
        assertThat(code(exchange(token, HttpMethod.POST, "/api/system/user/with-password", body)))
                .isEqualTo(FORBIDDEN);
        assertThat(userService.findByUsername("saf-cap-new-admin")).as("被拒后不能留下半个账号").isNull();

        assertThat(code(exchange(token, HttpMethod.PUT, "/api/system/user/" + peer + "/roles",
                List.of(smallRole)))).isEqualTo(OK);
        assertThat(userService.roleIdsOf(peer)).containsExactly(smallRole);
    }

    @Test
    @DisplayName("授权上限：给角色分配菜单不能超出自身菜单")
    void menuGrantIsCappedByOperatorMenus() {
        Long operatorRole = createRole("SAF_MENU_OP", DataScopeType.ALL,
                menuIds("system:role:list", "system:role:assign-menu"));
        Long target = createRole("SAF_MENU_TARGET", DataScopeType.SELF, List.of());
        createUser("saf-menu-op", null, List.of(operatorRole));
        String token = login("saf-menu-op");

        assertThat(code(exchange(token, HttpMethod.PUT, "/api/system/role/" + target + "/menus",
                menuIds("system:user:remove")))).isEqualTo(FORBIDDEN);
        assertThat(roleService.menuIdsOf(target)).isEmpty();

        assertThat(code(exchange(token, HttpMethod.PUT, "/api/system/role/" + target + "/menus",
                menuIds("system:role:list")))).isEqualTo(OK);
        assertThat(roleService.menuIdsOf(target)).contains(menuIds("system:role:list").get(0));
    }

    // ------------------------------------------------------------ S2 删除用户

    @Test
    @DisplayName("删除用户：旧 access token 立即 401、refresh 失效、角色关联清空；不能删自己")
    void deletingUserRevokesSessions() {
        Long smallRole = createRole("SAF_DEL_SMALL", DataScopeType.SELF, menuIds("system:user:list"));
        Long victim = createUser("saf-del-victim", null, List.of(smallRole));
        Map<String, Object> session = loginData("saf-del-victim");
        String victimToken = String.valueOf(session.get("token"));
        String refreshToken = String.valueOf(session.get("refreshToken"));
        assertThat(exchange(victimToken, HttpMethod.GET, "/api/auth/me", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        String admin = adminToken();
        assertThat(code(exchange(admin, HttpMethod.DELETE, "/api/system/user/" + victim, null))).isEqualTo(OK);

        assertThat(exchange(victimToken, HttpMethod.GET, "/api/auth/me", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<Map> refreshed = rest.postForEntity("/api/auth/refresh",
                json(Map.of("refreshToken", refreshToken)), Map.class);
        assertThat(code(refreshed)).isEqualTo(UNAUTHORIZED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE user_id = ?", Long.class, victim))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT deleted FROM sys_user WHERE id = ?", Integer.class, victim))
                .isEqualTo(1);

        assertThat(code(exchange(admin, HttpMethod.DELETE, "/api/system/user/" + adminUserId(), null)))
                .isEqualTo(BAD_REQUEST);
    }

    // ------------------------------------------------------------ S8 菜单成环

    @Test
    @DisplayName("菜单上级不能是自己、自己的子孙或不存在的菜单；有子菜单的菜单不能删")
    void menuParentMustNotFormCycle() {
        Long dir = createMenu("saf-cycle-dir", 0L);
        Long child = createMenu("saf-cycle-child", dir);
        String admin = adminToken();

        assertThat(code(exchange(admin, HttpMethod.PUT, "/api/system/menu/" + dir,
                Map.of("parentId", String.valueOf(dir))))).isEqualTo(BAD_REQUEST);
        assertThat(code(exchange(admin, HttpMethod.PUT, "/api/system/menu/" + dir,
                Map.of("parentId", String.valueOf(child))))).isEqualTo(BAD_REQUEST);
        assertThat(parentIdOf(dir)).isZero();

        assertThat(code(exchange(admin, HttpMethod.POST, "/api/system/menu",
                Map.of("menuName", "saf-cycle-orphan", "menuType", "DIR", "parentId", "999999999"))))
                .isEqualTo(BAD_REQUEST);

        assertThat(code(exchange(admin, HttpMethod.DELETE, "/api/system/menu/" + dir, null))).isEqualTo(BAD_REQUEST);
        assertThat(code(exchange(admin, HttpMethod.DELETE, "/api/system/menu/" + child, null))).isEqualTo(OK);
        assertThat(code(exchange(admin, HttpMethod.DELETE, "/api/system/menu/" + dir, null))).isEqualTo(OK);
    }

    // ------------------------------------------------------------ S9 审计字段

    @Test
    @DisplayName("审计字段由服务端决定：编辑改不了创建人、伪造不了更新人；新增指定不了创建人、删除标记与版本号")
    void auditFieldsCannotBeForgedByRequestBody() {
        String admin = adminToken();
        Long adminId = adminUserId();
        Map<String, Object> createBody = new HashMap<>();
        createBody.put("username", "saf-audit-user");
        createBody.put("password", PASSWORD);
        createBody.put("nickname", "审计字段");
        assertThat(code(exchange(admin, HttpMethod.POST, "/api/system/user/with-password", createBody))).isEqualTo(OK);
        Long userId = userService.findByUsername("saf-audit-user").getId();

        Map<String, Object> forged = new HashMap<>();
        forged.put("nickname", "审计字段-改");
        forged.put("createBy", "999999");
        forged.put("createTime", "2000-01-01 00:00:00");
        forged.put("updateBy", "888888");
        assertThat(code(exchange(admin, HttpMethod.PUT, "/api/system/user/" + userId, forged))).isEqualTo(OK);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT nickname, create_by, update_by FROM sys_user WHERE id = ?", userId);
        assertThat(row.get("nickname")).isEqualTo("审计字段-改");
        assertThat(((Number) row.get("create_by")).longValue()).isEqualTo(adminId);
        assertThat(((Number) row.get("update_by")).longValue()).isEqualTo(adminId);
        // 不用 YEAR()：MySQL 8.x 返回 YEAR 类型，驱动映射成 java.sql.Date，5.7 上却是数字
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = ? AND create_time < '2001-01-01'", Long.class, userId))
                .as("创建时间不应被改成请求体里的 2000 年").isZero();

        Map<String, Object> roleBody = new HashMap<>();
        roleBody.put("roleCode", "SAF_AUDIT_ROLE");
        roleBody.put("roleName", "审计字段角色");
        roleBody.put("createBy", "999999");
        roleBody.put("deleted", 1);
        roleBody.put("version", 7);
        assertThat(code(exchange(admin, HttpMethod.POST, "/api/system/role", roleBody))).isEqualTo(OK);
        Map<String, Object> roleRow = jdbc.queryForMap(
                "SELECT create_by, deleted, version FROM sys_role WHERE role_code = 'SAF_AUDIT_ROLE'");
        assertThat(((Number) roleRow.get("create_by")).longValue()).isEqualTo(adminId);
        assertThat(((Number) roleRow.get("deleted")).intValue()).isZero();
        assertThat(((Number) roleRow.get("version")).intValue()).isZero();
    }

    // ------------------------------------------------------------ 低风险 2：删除时引用校验

    @Test
    @DisplayName("有下级部门或下属用户的部门、仍分配给用户的角色不能删；角色删除后关联一并清理")
    void deletionIsRejectedWhileReferenced() {
        String admin = adminToken();
        Long parent = createDept("saf-ref-parent", 0L);
        Long child = createDept("saf-ref-child", parent);
        Long staffed = createDept("saf-ref-staffed", 0L);
        Long usedRole = createRole("SAF_REF_USED", DataScopeType.SELF, menuIds("system:user:list"));
        Long unusedRole = createRole("SAF_REF_UNUSED", DataScopeType.SELF, menuIds("system:user:list"));
        createUser("saf-ref-staff", staffed, List.of(usedRole));

        assertThat(code(exchange(admin, HttpMethod.DELETE, "/api/system/dept/" + parent, null))).isEqualTo(BAD_REQUEST);
        assertThat(code(exchange(admin, HttpMethod.DELETE, "/api/system/dept/" + staffed, null))).isEqualTo(BAD_REQUEST);
        assertThat(code(exchange(admin, HttpMethod.DELETE, "/api/system/dept/" + child, null))).isEqualTo(OK);
        assertThat(code(exchange(admin, HttpMethod.DELETE, "/api/system/dept/" + parent, null))).isEqualTo(OK);

        assertThat(code(exchange(admin, HttpMethod.DELETE, "/api/system/role/" + usedRole, null))).isEqualTo(BAD_REQUEST);
        assertThat(code(exchange(admin, HttpMethod.DELETE, "/api/system/role/" + unusedRole, null))).isEqualTo(OK);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = ?", Long.class, unusedRole))
                .isZero();
    }

    // ---------------------------------------------------------------- helpers

    private Long createDept(String name, Long parentId) {
        SysDept dept = new SysDept();
        dept.setDeptName(name);
        dept.setParentId(parentId);
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

    private Long createUser(String username, Long deptId, List<Long> roleIds) {
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setNickname(username);
        u.setDeptId(deptId);
        Long id = userService.createUser(u, PASSWORD, roleIds).getId();
        clearPwdResetRequired(username);
        return id;
    }

    private Long createMenu(String name, Long parentId) {
        SysMenu menu = new SysMenu();
        menu.setMenuName(name);
        menu.setMenuType(SysMenu.TYPE_DIR);
        menu.setParentId(parentId);
        menu.setPath("/" + name);
        menu.setSort(0);
        menuService.save(menu);
        return menu.getId();
    }

    private List<Long> menuIds(String... permCodes) {
        List<SysMenu> all = menuService.list();
        return Arrays.stream(permCodes)
                .map(code -> all.stream()
                        .filter(m -> code.equals(m.getPermCode()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("种子数据缺少权限点: " + code))
                        .getId())
                .toList();
    }

    private Long adminRoleId() {
        return jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code = 'ADMIN' AND deleted = 0", Long.class);
    }

    private Long adminUserId() {
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE username = 'admin' AND deleted = 0", Long.class);
    }

    private Long parentIdOf(Long menuId) {
        return jdbc.queryForObject("SELECT parent_id FROM sys_menu WHERE id = ?", Long.class, menuId);
    }

    private String adminToken() {
        return String.valueOf(loginData("admin", devSeedAdminPassword()).get("token"));
    }

    private String login(String username) {
        return String.valueOf(loginData(username).get("token"));
    }

    private Map<String, Object> loginData(String username) {
        return loginData(username, PASSWORD);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loginData(String username, String password) {
        ResponseEntity<Map> resp = rest.postForEntity("/api/auth/login",
                json(Map.of("type", "password", "username", username, "password", password)), Map.class);
        assertThat(code(resp)).as("前置条件：登录应成功: " + username).isEqualTo(OK);
        return (Map<String, Object>) resp.getBody().get("data");
    }

    private ResponseEntity<Map> exchange(String token, HttpMethod method, String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        headers.setBearerAuth(token);
        return rest.exchange(url, method, new HttpEntity<>(body, headers), Map.class);
    }

    private static int code(ResponseEntity<Map> resp) {
        assertThat(resp.getBody()).as("响应体不应为空，HTTP " + resp.getStatusCode()).isNotNull();
        return ((Number) resp.getBody().get("code")).intValue();
    }

    private static HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        return new HttpEntity<>(body, headers);
    }
}
