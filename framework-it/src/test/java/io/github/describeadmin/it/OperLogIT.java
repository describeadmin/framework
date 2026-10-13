package io.github.describeadmin.it;

import io.github.describeadmin.common.api.PageQuery;
import io.github.describeadmin.common.api.PageResult;
import io.github.describeadmin.system.entity.SysOperLog;
import io.github.describeadmin.system.service.SysOperLogService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 操作日志的 HTTP 端到端验证。
 *
 * <p>两条捕获路径都要覆盖，与 {@code OperLogAspect} 的类注释描述的两条 pointcut 对应：
 * {@code BaseController} 通用写端点自动记录、自定义端点靠 {@code @OperLog} 注解记录；
 * 外加"失败的业务操作也要落日志"这条容易被漏掉的路径。
 *
 * <p>成功路径一律断言<b>恰好一条</b>，不要退回 {@code anyMatch}："至少有一条"对重复记录是瞎的——
 * {@code SysUserController.update} 两条 pointcut 同时命中、编辑一次落两条，就是这样漏过去的。
 *
 * <p>断言具体值而不是只看行数——尤其是脱敏那条，必须确认密码<b>确实被替换成了</b>
 * {@code ***}，而不是没有被记录（那样同样看不到明文，但意味着脱敏根本没生效，
 * 只是恰好用另一种方式"看起来安全"）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("操作日志（HTTP 端到端）")
class OperLogIT extends AbstractMySqlIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired SysOperLogService operLogService;

    @Test
    @DisplayName("BaseController 的通用写端点自动记录，覆写了 create 的 SysDeptController 也不例外")
    void baseControllerWriteIsLoggedAutomatically() {
        String token = tokenOfAdmin();
        String deptName = "oper-log-dept-" + System.nanoTime();

        ResponseEntity<String> resp = rest.exchange("/api/system/dept", HttpMethod.POST,
                new HttpEntity<>(Map.of("deptName", deptName, "parentId", 0, "sort", 1), bearer(token)),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<SysOperLog> records = logsContaining("system:dept", deptName);
        assertThat(records)
                .as("SysDeptController 覆写了 create()，但 execution(BaseController+.create(..)) "
                        + "按类型层级匹配，覆写不影响它仍是一次 BaseController.create() 执行")
                .hasSize(1);
        assertThat(records.get(0).getDescription()).isEqualTo("system:dept 新增");
        assertThat(records.get(0).getStatus()).isEqualTo(SysOperLog.STATUS_SUCCESS);
    }

    @Test
    @DisplayName("覆写通用端点又标了 @OperLog 的，一次请求只落一条日志，描述取注解")
    void overriddenEndpointWithAnnotationIsLoggedOnce() {
        String token = tokenOfAdmin();
        String username = "oper-log-once-" + System.nanoTime();
        ResponseEntity<Map> created = rest.exchange("/api/system/user/with-password", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", username, "password", "P@ssw0rd-123",
                        "nickname", "改前"), bearer(token)),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> user = (Map<?, ?>) created.getBody().get("data");
        Object userId = user.get("id");

        // SysUserController.update 覆写了 BaseController.update 并标了 @OperLog("更新用户")，
        // 通用 pointcut 与注解 pointcut 都能匹配它——必须只有注解那条生效。
        String nickname = "改后-" + System.nanoTime();
        Map<String, Object> body = new HashMap<>();
        body.put("nickname", nickname);
        body.put("version", user.get("version"));
        ResponseEntity<String> resp = rest.exchange("/api/system/user/" + userId, HttpMethod.PUT,
                new HttpEntity<>(body, bearer(token)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).as("编辑应成功").contains(nickname);

        List<SysOperLog> records = logsContaining("system:user", nickname);
        assertThat(records)
                .as("一次编辑只能落一条日志；两条说明通用路径没有排除 @OperLog 方法")
                .hasSize(1);
        assertThat(records.get(0).getDescription()).isEqualTo("更新用户");
        assertThat(records.get(0).getHttpMethod()).isEqualTo("PUT");
        assertThat(records.get(0).getRequestUrl()).isEqualTo("/api/system/user/" + userId);
    }

    @Test
    @DisplayName("自定义端点靠 @OperLog 注解记录，且密码字段被脱敏")
    void annotatedEndpointIsLoggedWithMaskedPassword() {
        String token = tokenOfAdmin();
        String username = "oper-log-user-" + System.nanoTime();

        ResponseEntity<String> resp = rest.exchange("/api/system/user/with-password", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", username, "password", "P@ssw0rd-123",
                        "nickname", "脱敏测试"), bearer(token)),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<SysOperLog> records = logsContaining("system:user", username);
        assertThat(records).as("一次创建只能落一条日志").hasSize(1);
        SysOperLog entry = records.get(0);

        assertThat(entry.getDescription()).isEqualTo("创建用户");
        assertThat(entry.getStatus()).isEqualTo(SysOperLog.STATUS_SUCCESS);
        assertThat(entry.getOperatorName()).isEqualTo("admin");
        assertThat(entry.getRequestParam())
                .as("密码字段必须被脱敏，不能明文落库")
                .doesNotContain("P@ssw0rd-123")
                .contains("\"password\":\"***\"");
    }

    @Test
    @DisplayName("失败的业务操作也落日志，status 标记失败并记录错误信息")
    void failedBusinessOperationIsLoggedAsFailed() {
        String token = tokenOfAdmin();

        // 用户名 admin 已存在，createUser 会抛 BizException——GlobalExceptionHandler
        // 把业务异常映射成 HTTP 200，但异常本身仍会先经过 OperLogAspect 的 catch 块。
        ResponseEntity<String> resp = rest.exchange("/api/system/user/with-password", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", "admin", "password", "whatever"), bearer(token)),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).as("业务异常应体现在响应体里").contains("用户名已存在");

        assertThat(logsOfModule("system:user"))
                .as("失败的操作也应该落一条日志，status 标记失败，而不是被静默吞掉")
                .anyMatch(log -> "创建用户".equals(log.getDescription())
                        && log.getStatus() == SysOperLog.STATUS_FAIL
                        && log.getErrorMsg() != null
                        && log.getErrorMsg().contains("用户名已存在"));
    }

    // ---------------------------------------------------------------- helpers

    /** 本次请求落下的日志：按模块取，再用请求参数里的唯一标记收窄，不受其他用例写入的干扰。 */
    private List<SysOperLog> logsContaining(String module, String marker) {
        return logsOfModule(module).stream()
                .filter(log -> log.getRequestParam() != null && log.getRequestParam().contains(marker))
                .toList();
    }

    private List<SysOperLog> logsOfModule(String module) {
        PageQuery query = new PageQuery();
        query.setSize(500);
        PageResult<SysOperLog> page = operLogService.page(query, module, null, null, null, null);
        return page.getRecords();
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
