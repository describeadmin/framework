package io.github.describeadmin.system.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OperLogAspect} 参数脱敏逻辑的单元测试。
 *
 * <p>不起 Spring 上下文——{@link OperLogAspect#serializeArgs} 是纯粹的字符串拼装逻辑，
 * {@link SysOperLogService} 在这条路径上完全用不到，传 {@code null} 即可。
 * 这是唯一必须做对的安全底线：漏了脱敏，明文密码就会原样进 {@code sys_oper_log}。
 */
@DisplayName("OperLogAspect 参数脱敏")
class OperLogAspectTest {

    private final OperLogAspect aspect = new OperLogAspect(null, new ObjectMapper());

    @Test
    @DisplayName("password/pwd/secret/token 字段整段替换为 ***，大小写不敏感")
    void masksSensitiveKeys() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "zhangsan");
        body.put("password", "P@ssw0rd-123");
        body.put("PWD", "another-secret");
        body.put("accessToken", "abc.def.ghi");
        body.put("nickname", "张三");

        String json = aspect.serializeArgs(new Object[]{body});

        assertThat(json).contains("\"password\":\"***\"");
        assertThat(json).contains("\"PWD\":\"***\"");
        assertThat(json).contains("\"accessToken\":\"***\"");
        assertThat(json)
                .as("非敏感字段原样保留")
                .contains("\"username\":\"zhangsan\"")
                .contains("\"nickname\":\"张三\"");
        assertThat(json)
                .as("明文密码不能出现在任何地方")
                .doesNotContain("P@ssw0rd-123")
                .doesNotContain("another-secret")
                .doesNotContain("abc.def.ghi");
    }

    @Test
    @DisplayName("嵌套对象里的敏感字段同样被脱敏")
    void masksNestedSensitiveKeys() {
        Map<String, Object> credentials = new LinkedHashMap<>();
        credentials.put("password", "nested-secret");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "lisi");
        body.put("credentials", credentials);

        String json = aspect.serializeArgs(new Object[]{body});

        assertThat(json).contains("\"password\":\"***\"");
        assertThat(json).doesNotContain("nested-secret");
    }

    @Test
    @DisplayName("参数序列化结果超过 2000 字符时被截断")
    void truncatesLongParams() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("remark", "x".repeat(3000));

        String json = aspect.serializeArgs(new Object[]{body});

        assertThat(json.length()).isEqualTo(2000);
    }

    @Test
    @DisplayName("没有敏感字段时原样序列化，不误伤")
    void doesNotTouchNonSensitiveArgs() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deptName", "总部");
        body.put("parentId", 0);

        String json = aspect.serializeArgs(new Object[]{body});

        assertThat(json).contains("\"deptName\":\"总部\"").contains("\"parentId\":0");
    }
}
