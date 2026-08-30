package io.github.describeadmin.mybatis.autoconfigure;

import com.baomidou.mybatisplus.annotation.DbType;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * framework-mybatis-starter 的配置项，前缀 {@code describeadmin.mybatis}。
 */
@ConfigurationProperties(prefix = "describeadmin.mybatis")
public class FrameworkMybatisProperties {

    /**
     * 分页方言。
     *
     * <p><b>不要在代码里硬编码 {@code DbType.MYSQL}</b>（见 CLAUDE.md 3.5）。
     * 默认 {@code MYSQL} 走 {@code LIMIT/OFFSET} 通用方言，MySQL 5.7 与 8.x 均兼容；
     * 目标库为达梦、金仓、OceanBase 等国产化数据库时，业务方通过本配置项切换。
     */
    private DbType dbType = DbType.MYSQL;

    /** 单页最大条数上限，防止业务方传入超大 size 拖垮数据库。 */
    private long maxLimit = 500L;

    /** 是否开启溢出总页数后进行处理（true 时页码超出总页数会返回首页而非空集）。 */
    private boolean overflow = false;

    /** 数据权限。 */
    private final DataScope dataScope = new DataScope();

    public DbType getDbType() {
        return dbType;
    }

    public void setDbType(DbType dbType) {
        this.dbType = dbType;
    }

    public long getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(long maxLimit) {
        this.maxLimit = maxLimit;
    }

    public boolean isOverflow() {
        return overflow;
    }

    public void setOverflow(boolean overflow) {
        this.overflow = overflow;
    }

    public DataScope getDataScope() {
        return dataScope;
    }

    /**
     * 数据权限，前缀 {@code describeadmin.mybatis.data-scope}。
     */
    public static class DataScope {

        /**
         * 是否启用数据权限拦截器。
         *
         * <p>关闭后行为等同于没有引入这个机制——已登记参与数据权限的表也不再被过滤。
         * 仅建议在排查问题时临时关闭。
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
