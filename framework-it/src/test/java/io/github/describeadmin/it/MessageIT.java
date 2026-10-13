package io.github.describeadmin.it;

import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.system.api.MessageDraft;
import io.github.describeadmin.system.api.MessageSender;
import io.github.describeadmin.system.entity.SysUser;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 站内信的 HTTP 端到端验证：只能看到、改到自己的消息；批量已读、按业务键收尾、清空已读；
 * 发送人展示不受数据权限影响；发送跟随业务事务回滚。
 *
 * <p>标题一律按具体中文值断言，不只比条数（CLAUDE.md 3.6）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("站内信（HTTP 端到端）")
class MessageIT extends AbstractMySqlIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired SysUserService userService;
    @Autowired MessageSender sender;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private static final String PASSWORD = "pwd-12345";

    @Test
    @DisplayName("收件人只看得到自己的消息；unreadCount 是数字、id 是字符串；发送人昵称回填")
    void inboxIsPerUser() {
        Long from = createUser("msg-from", "工单发起人");
        Long alice = createUser("msg-alice", "msg-alice");
        Long bob = createUser("msg-bob", "msg-bob");
        sender.send(List.of(alice, bob, alice), new MessageDraft("workorder.approval", "收到待审批工单",
                "张三提交了「服务器扩容」工单，请审批", "/workorder/detail?id=1", "workorder:1", from));
        sender.send(List.of(bob), MessageDraft.of("system.notice", "仅发给 bob 的系统消息", null, null));

        Map<String, Object> inbox = inbox(login("msg-alice"));
        assertThat(inbox.get("unreadCount")).as("计数保持数字形态").isEqualTo(1);
        List<Map<String, Object>> latest = list(inbox.get("latest"));
        assertThat(latest).as("重复的收件人只发一条").hasSize(1);
        Map<String, Object> msg = latest.get(0);
        assertThat(msg.get("id")).as("Long 序列化为字符串（4.8）").isInstanceOf(String.class);
        assertThat(msg.get("title")).isEqualTo("收到待审批工单");
        assertThat(msg.get("content")).isEqualTo("张三提交了「服务器扩容」工单，请审批");
        assertThat(msg.get("link")).isEqualTo("/workorder/detail?id=1");
        assertThat(msg.get("category")).isEqualTo("workorder.approval");
        assertThat(msg.get("senderName")).as("SELF 档收件人也能看到发送人").isEqualTo("工单发起人");
        assertThat(msg.get("readTime")).isNull();

        List<String> bobTitles = titles(inbox(login("msg-bob")));
        assertThat(bobTitles).containsExactly("仅发给 bob 的系统消息", "收到待审批工单");
    }

    @Test
    @DisplayName("标别人的消息已读、删别人的消息都报不存在，且对方数据不变")
    void cannotTouchOthersMessages() {
        Long carol = createUser("msg-carol", "msg-carol");
        createUser("msg-dave", "msg-dave");
        sender.send(List.of(carol), MessageDraft.of("system.notice", "carol 的消息", null, null));
        String carolId = idOf(inbox(login("msg-carol")), "carol 的消息");

        String dave = login("msg-dave");
        assertThat(code(rest.exchange("/api/auth/messages/" + carolId + "/read", HttpMethod.PUT,
                new HttpEntity<>(bearer(dave)), Map.class))).isEqualTo(ResultCode.NOT_FOUND.getCode());
        assertThat(code(rest.exchange("/api/auth/messages/" + carolId, HttpMethod.DELETE,
                new HttpEntity<>(bearer(dave)), Map.class))).isEqualTo(ResultCode.NOT_FOUND.getCode());

        Map<String, Object> carolInbox = inbox(login("msg-carol"));
        assertThat(carolInbox.get("unreadCount")).isEqualTo(1);
        assertThat(titles(carolInbox)).containsExactly("carol 的消息");
    }

    @Test
    @DisplayName("单条已读幂等；全部已读只影响自己；清空只删已读、保留未读")
    void readAndClear() {
        Long erin = createUser("msg-erin", "msg-erin");
        Long frank = createUser("msg-frank", "msg-frank");
        sender.send(List.of(erin, frank), MessageDraft.of("system.notice", "第一条", null, null));
        sender.send(List.of(erin, frank), MessageDraft.of("system.notice", "第二条", null, null));
        String token = login("msg-erin");
        String first = idOf(inbox(token), "第一条");

        assertThat(code(put("/api/auth/messages/" + first + "/read", token))).isZero();
        assertThat(code(put("/api/auth/messages/" + first + "/read", token))).as("重复标已读不报错").isZero();
        assertThat(inbox(token).get("unreadCount")).isEqualTo(1);

        ResponseEntity<Map> cleared = rest.exchange("/api/auth/messages/read", HttpMethod.DELETE,
                new HttpEntity<>(bearer(token)), Map.class);
        assertThat(data(cleared)).as("只删了一条已读").isEqualTo(1);
        assertThat(titles(inbox(token))).as("未读的第二条保留").containsExactly("第二条");

        assertThat(data(put("/api/auth/messages/read-all", token))).isEqualTo(1);
        assertThat(inbox(token).get("unreadCount")).isEqualTo(0);
        assertThat(inbox(login("msg-frank")).get("unreadCount")).as("别人不受影响").isEqualTo(2);
    }

    @Test
    @DisplayName("markReadByBiz：或签场景下所有审批人的同一条待办一起变为已读，其他业务键不受影响")
    void markReadByBizClosesForEveryone() {
        Long a = createUser("msg-approver-a", "msg-approver-a");
        Long b = createUser("msg-approver-b", "msg-approver-b");
        sender.send(List.of(a, b), new MessageDraft("workorder.approval", "待审批 #7", null,
                "/workorder/detail?id=7", "workorder:7", null));
        sender.send(List.of(a), new MessageDraft("workorder.approval", "待审批 #8", null,
                "/workorder/detail?id=8", "workorder:8", null));

        assertThat(sender.markReadByBiz("workorder.approval", "workorder:7")).isEqualTo(2);

        assertThat(inbox(login("msg-approver-a")).get("unreadCount")).isEqualTo(1);
        assertThat(inbox(login("msg-approver-b")).get("unreadCount")).isEqualTo(0);
    }

    @Test
    @DisplayName("消息中心分页：unread=true 只看未读，category 过滤生效")
    void pageFilters() {
        Long gina = createUser("msg-gina", "msg-gina");
        sender.send(List.of(gina), MessageDraft.of("workorder.approval", "工单消息", null, null));
        sender.send(List.of(gina), MessageDraft.of("comment.reply", "回复消息", null, null));
        String token = login("msg-gina");
        put("/api/auth/messages/" + idOf(inbox(token), "回复消息") + "/read", token);

        assertThat(pageTitles(token, "unread=true")).containsExactly("工单消息");
        assertThat(pageTitles(token, "category=comment.reply")).containsExactly("回复消息");
        assertThat(pageTitles(token, "current=1&size=10")).containsExactly("回复消息", "工单消息");
    }

    @Test
    @DisplayName("发送跟随业务事务：业务回滚，消息不落库")
    void sendRollsBackWithBusinessTransaction() {
        Long henry = createUser("msg-henry", "msg-henry");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            sender.send(List.of(henry), MessageDraft.of("system.notice", "不该出现的消息", null, null));
            throw new IllegalStateException("业务失败");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_message WHERE user_id = ?",
                Integer.class, henry)).isZero();
    }

    @Test
    @DisplayName("未登录访问收件箱 401；收件人超限抛错不截断")
    void guards() {
        assertThat(rest.getForEntity("/api/auth/messages/inbox", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        Long[] many = new Long[MessageSender.MAX_RECIPIENTS + 1];
        for (int i = 0; i < many.length; i++) {
            many[i] = 900_000L + i;
        }
        assertThatThrownBy(() -> sender.send(Arrays.asList(many),
                MessageDraft.of("system.notice", "超限", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_message WHERE title = '超限'",
                Integer.class)).isZero();
    }

    // ---------------------------------------------------------------- helpers

    private Long createUser(String username, String nickname) {
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setNickname(nickname);
        SysUser created = userService.createUser(u, PASSWORD, List.of());
        clearPwdResetRequired(username);
        return created.getId();
    }

    private String login(String username) {
        ResponseEntity<Map> resp = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("type", "password", "username", username, "password", PASSWORD),
                        jsonHeaders()), Map.class);
        assertThat(resp.getStatusCode()).as("登录应成功: " + username).isEqualTo(HttpStatus.OK);
        return String.valueOf(((Map<?, ?>) resp.getBody().get("data")).get("token"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> inbox(String token) {
        return (Map<String, Object>) data(rest.exchange("/api/auth/messages/inbox", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), Map.class));
    }

    @SuppressWarnings("unchecked")
    private List<String> pageTitles(String token, String query) {
        Map<String, Object> page = (Map<String, Object>) data(rest.exchange("/api/auth/messages?" + query,
                HttpMethod.GET, new HttpEntity<>(bearer(token)), Map.class));
        return list(page.get("records")).stream().map(m -> String.valueOf(m.get("title"))).toList();
    }

    private ResponseEntity<Map> put(String url, String token) {
        return rest.exchange(url, HttpMethod.PUT, new HttpEntity<>(bearer(token)), Map.class);
    }

    private static List<String> titles(Map<String, Object> inbox) {
        return list(inbox.get("latest")).stream().map(m -> String.valueOf(m.get("title"))).toList();
    }

    private static String idOf(Map<String, Object> inbox, String title) {
        return list(inbox.get("latest")).stream()
                .filter(m -> title.equals(m.get("title")))
                .map(m -> String.valueOf(m.get("id")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("收件箱里没有: " + title));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static int code(ResponseEntity<Map> resp) {
        assertThat(resp.getStatusCode()).as("业务错误也应是 HTTP 200: " + resp.getBody()).isEqualTo(HttpStatus.OK);
        return ((Number) resp.getBody().get("code")).intValue();
    }

    private static Object data(ResponseEntity<Map> resp) {
        assertThat(code(resp)).as(String.valueOf(resp.getBody())).isZero();
        return resp.getBody().get("data");
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
