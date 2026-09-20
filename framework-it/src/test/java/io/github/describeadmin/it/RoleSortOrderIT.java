package io.github.describeadmin.it;

import io.github.describeadmin.system.core.HomePathResolver;
import io.github.describeadmin.system.entity.SysRole;
import io.github.describeadmin.system.mapper.RoleScope;
import io.github.describeadmin.system.mapper.SysRelationMapper;
import io.github.describeadmin.system.service.SysRoleService;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 角色 {@code sort} 字段的两个生效点。
 *
 * <p>{@code sort} 原本只在 {@code SysRelationMapper} 的两条查询里参与排序，
 * 角色列表端点自己<b>不</b>按它排——于是"排序"这一列填什么都看不出区别，
 * 界面上完全无从判断这个字段是坏了还是本来就没用。部门/菜单/字典数据都在
 * 各自 Service 里 {@code orderByAsc("sort")}，只有角色漏了。
 *
 * <p>另一半是 {@code sort} 允许重复（默认值 0，截图里两个角色都是 0 属常态），
 * 而原先的 {@code ORDER BY r.sort} 没有二级排序键。同值行的相对顺序由数据库
 * 自行决定，{@link HomePathResolver} 又恰好是"取第一个非空"的语义——
 * 用户身兼两个同 {@code sort} 且都配了首页的角色时，<b>登录落哪个首页会在两次
 * 之间漂移</b>，而数据与配置一字未改。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("角色 sort 的生效点")
class RoleSortOrderIT extends AbstractMySqlIntegrationTest {

    /** 角色标识前缀，用于把本组造的数据从共享容器里的其他测试数据中筛出来。 */
    private static final String PREFIX = "ZZSORT";

    @Autowired TestRestTemplate rest;
    @Autowired SysRoleService roleService;
    @Autowired SysRelationMapper relationMapper;

    @Test
    @DisplayName("角色列表按 sort 升序，sort 相同时按 id 升序")
    void listIsOrderedBySortThenId() {
        // 刻意让创建顺序（= 主键顺序）与期望的展示顺序不一致：
        // 若列表没有 ORDER BY，返回的就是 A/B/C 这个创建顺序，断言随即失败。
        SysRole a = createRole(PREFIX + "_A", 5);
        SysRole b = createRole(PREFIX + "_B", 1);
        SysRole c = createRole(PREFIX + "_C", 1);

        List<String> codes = listRoleCodes();

        assertThat(codes)
                .as("sort 小的在前；B/C 同为 1，按 id 升序 → 先建的 B 在前")
                .containsExactly(b.getRoleCode(), c.getRoleCode(), a.getRoleCode());
        assertThat(codes)
                .as("创建顺序是 A/B/C——返回值与它一致说明列表根本没按 sort 排")
                .isNotEqualTo(List.of(a.getRoleCode(), b.getRoleCode(), c.getRoleCode()));
    }

    @Test
    @DisplayName("sort 相同的多角色，首页仲裁落在 id 较小的那个上")
    void homePathArbitrationIsDeterministicOnEqualSort() {
        // 两个角色 sort 完全相同，且都配了 home_path——这正是仲裁结果会漂移的形状
        SysRole first = createRole(PREFIX + "_H1", 7, "/system/user");
        SysRole second = createRole(PREFIX + "_H2", 7, "/system/dept");

        Long userId = createUserId("zzsort-home");
        relationMapper.insertUserRoles(userId, List.of(second.getId(), first.getId()));

        List<RoleScope> scopes = relationMapper.selectDataScopesByUserId(userId);
        List<Long> roleIds = new ArrayList<>();
        scopes.forEach(s -> roleIds.add(s.getRoleId()));

        assertThat(roleIds)
                .as("插入顺序刻意反着来，排序结果不能受它影响")
                .isSortedAccordingTo(Comparator.naturalOrder());
        // 比对具体值而不是"非空"（CLAUDE.md §3.6）：漂移时这里拿到的是另一个角色的路径
        assertThat(HomePathResolver.resolve(scopes))
                .as("同 sort 时由 id 二级排序定胜负，先建的 H1 赢")
                .isEqualTo("/system/user");
    }

    // ------------------------------------------------------------------ 工具

    private SysRole createRole(String roleCode, int sort) {
        return createRole(roleCode, sort, null);
    }

    /** 直接走 Service 建角色：本组测的是排序，不必再绕一遍权限校验。 */
    private SysRole createRole(String roleCode, int sort, String homePath) {
        SysRole role = new SysRole();
        role.setRoleCode(roleCode);
        role.setRoleName("排序回归 " + roleCode);
        role.setSort(sort);
        role.setHomePath(homePath);
        roleService.save(role);
        assertThat(role.getId()).as("前置条件：建角色应回填主键").isNotNull();
        return role;
    }

    /**
     * 取本组造的角色的 role_code，按端点返回的原始顺序。
     *
     * <p>用 {@code roleCode} 前缀筛一道：共享容器里其他测试也会建角色，
     * 不筛的话断言会被无关数据打断。
     */
    private List<String> listRoleCodes() {
        ResponseEntity<Map> resp = rest.exchange(
                "/api/system/role?roleCode=" + PREFIX + "_&size=50", HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOfAdmin())), Map.class);
        assertThat(resp.getStatusCode()).as("前置条件：ADMIN 查角色列表应成功").isEqualTo(HttpStatus.OK);

        Map<?, ?> page = (Map<?, ?>) resp.getBody().get("data");
        List<?> records = (List<?>) page.get("records");
        List<String> codes = new ArrayList<>();
        records.forEach(r -> codes.add(String.valueOf(((Map<?, ?>) r).get("roleCode"))));
        return codes;
    }

    /** 建一个账号，只为拿到一个能挂角色的 user_id。 */
    private Long createUserId(String username) {
        ResponseEntity<Map> created = rest.exchange("/api/system/user/with-password",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "username", username,
                        "password", "pwd-12345",
                        "nickname", "排序回归",
                        "status", 1), bearer(tokenOfAdmin())),
                Map.class);
        assertThat(created.getStatusCode()).as("前置条件：建号应成功").isEqualTo(HttpStatus.OK);
        // id 按框架的全局约定（CLAUDE.md §4.8）序列化为字符串，不能直接转 Long
        Object id = ((Map<?, ?>) created.getBody().get("data")).get("id");
        return Long.valueOf(String.valueOf(id));
    }

    private String tokenOfAdmin() {
        ResponseEntity<Map> resp = rest.postForEntity("/api/auth/login",
                json(Map.of("type", "password", "username", "admin", "password", devSeedAdminPassword())),
                Map.class);
        assertThat(resp.getStatusCode()).as("登录应成功，检查种子数据").isEqualTo(HttpStatus.OK);
        return String.valueOf(((Map<?, ?>) resp.getBody().get("data")).get("token"));
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
