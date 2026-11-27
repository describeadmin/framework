package io.github.describeadmin.it;

import io.github.describeadmin.security.api.AuthRequest;
import io.github.describeadmin.security.api.TokenStore;
import io.github.describeadmin.security.core.AuthProviderRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class SystemPaginationIT extends AbstractMySqlIntegrationTest {

    private static final String PREFIX = "pagination-check-";
    private static final String MODULE = "pagination-check";

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired AuthProviderRegistry authRegistry;
    @Autowired TokenStore tokenStore;

    @Test
    void ordersSystemListsAcrossPageBoundaries() throws Exception {
        // c 的 ID 最大但时间最早，a 和 b 时间相同。角色和字典项中 a 的 sort 最大。
        for (String suffix : List.of("a", "b", "c")) {
            String name = PREFIX + suffix;
            String time = suffix.equals("c") ? "2024-01-01 12:00:00" : "2024-02-02 12:00:00";
            int sort = suffix.equals("a") ? 2 : 1;
            jdbc.update("INSERT INTO sys_user (username, password, create_time) VALUES (?, ?, ?)",
                    name, "unused", time);
            jdbc.update("INSERT INTO sys_dict_type (dict_type, dict_name, create_time) VALUES (?, ?, ?)",
                    name, name, time);
            jdbc.update("INSERT INTO sys_config (config_key, config_name, config_value, create_time) VALUES (?, ?, ?, ?)",
                    name, name, suffix, time);
            jdbc.update("INSERT INTO sys_oper_log (module, description, create_time) VALUES (?, ?, ?)",
                    MODULE, name, time);
            jdbc.update("INSERT INTO sys_role (role_code, role_name, sort, create_time) VALUES (?, ?, ?, ?)",
                    name, name, sort, time);
            jdbc.update("INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, sort, create_time) VALUES (?, ?, ?, ?, ?)",
                    PREFIX + "a", name, suffix, sort, time);
        }

        String token = tokenStore.issue(authRegistry.authenticate(new AuthRequest("password",
                Map.of("username", "admin", "password", devSeedAdminPassword()))));
        try {
            assertPages(token, "/api/system/user?username=" + PREFIX, "username", "c", "b", "a");
            assertPages(token, "/api/system/dict/type?dictType=" + PREFIX, "dictType", "c", "b", "a");
            assertPages(token, "/api/system/config?configKey=" + PREFIX, "configKey", "c", "b", "a");
            assertPages(token, "/api/system/oper-log?module=" + MODULE, "description", "c", "b", "a");
            assertPages(token, "/api/system/role?roleCode=" + PREFIX, "roleCode", "b", "c", "a");
            assertPages(token, "/api/system/dict/data?dictType=" + PREFIX + "a", "dictLabel", "b", "c", "a");
            assertPages(token, "/api/system/oper-log?module=" + MODULE
                            + "&start=2024-02-02T00:00:00&end=2024-02-02T23:59:59",
                    "description", "b", "a");
        } finally {
            tokenStore.revoke(token);
        }
    }

    private void assertPages(String token, String url, String field, String... suffixes) throws Exception {
        for (int i = 0; i < suffixes.length; i++) {
            mvc.perform(get(url).header("Authorization", "Bearer " + token)
                            .param("size", "1").param("current", String.valueOf(i + 1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(suffixes.length))
                    .andExpect(jsonPath("$.data.records.length()").value(1))
                    .andExpect(jsonPath("$.data.records[0]." + field).value(PREFIX + suffixes[i]));
        }
    }
}
