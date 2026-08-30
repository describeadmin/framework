package io.github.describeadmin.system.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * framework-system-starter 的配置项，前缀 {@code describeadmin.system}。
 */
@ConfigurationProperties(prefix = "describeadmin.system")
public class FrameworkSystemProperties {

    private final Dict dict = new Dict();
    private final Config config = new Config();
    private final OperLog operLog = new OperLog();
    private final DevSeed devSeed = new DevSeed();

    public Dict getDict() {
        return dict;
    }

    public Config getConfig() {
        return config;
    }

    public OperLog getOperLog() {
        return operLog;
    }

    public DevSeed getDevSeed() {
        return devSeed;
    }

    /** 字典，前缀 {@code describeadmin.system.dict}。 */
    public static class Dict {

        /** 按字典类型查询的结果缓存多久。字典数据读多写少，写操作后会主动失效，
         * 这里的 TTL 只是双保险，不必设得很短。 */
        private Duration cacheTtl = Duration.ofMinutes(30);

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }
    }

    /** 参数配置，前缀 {@code describeadmin.system.config}。 */
    public static class Config {

        /** 按 key 查询的结果缓存多久，取舍同 {@link Dict#cacheTtl}。 */
        private Duration cacheTtl = Duration.ofMinutes(30);

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }
    }

    /** 操作日志，前缀 {@code describeadmin.system.oper-log}。 */
    public static class OperLog {

        /**
         * 是否启用操作日志切面。
         *
         * <p>关闭后行为等同于没有引入这个机制——{@code BaseController} 的通用写端点
         * 与已标注 {@code @OperLog} 的自定义端点都不再记录。仅建议在排查问题
         * （如怀疑切面本身拖慢了请求）时临时关闭。
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 开发种子管理员，前缀 {@code describeadmin.system.dev-seed}。
     *
     * <p><b>默认关闭，只应在 {@code application-local.yml} 里打开</b>——生产 profile 永不装配。
     * 开启后 {@code DevAdminSeeder} 在库里没有任何用户时创建一个管理员账号，口令<b>随机生成</b>
     * （符合 {@code PasswordPolicy}），BCrypt 入库，明文写到 {@link #passwordFile} 并打印到启动日志。
     * 因此仓库里不再有任何固定默认口令。幂等：库里已有用户则整体跳过。
     */
    public static class DevSeed {

        /** 是否启用开发种子管理员。默认 {@code false}。 */
        private boolean enabled = false;

        /** 明文口令写到哪个文件，相对启动目录（{@code ${user.dir}}）解析。 */
        private String passwordFile = ".passwd";

        /** 种子管理员的用户名。 */
        private String adminUsername = "admin";

        /** 绑定的角色标识，必须是 seed-rbac.sql 已建好的 {@code sys_role.role_code}。 */
        private String adminRoleCode = "ADMIN";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPasswordFile() {
            return passwordFile;
        }

        public void setPasswordFile(String passwordFile) {
            this.passwordFile = passwordFile;
        }

        public String getAdminUsername() {
            return adminUsername;
        }

        public void setAdminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
        }

        public String getAdminRoleCode() {
            return adminRoleCode;
        }

        public void setAdminRoleCode(String adminRoleCode) {
            this.adminRoleCode = adminRoleCode;
        }
    }
}
