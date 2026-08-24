package io.github.describeadmin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.describeadmin.cache.api.CacheProvider;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.mybatis.api.BaseService;
import io.github.describeadmin.system.autoconfigure.FrameworkSystemProperties;
import io.github.describeadmin.system.entity.SysConfig;
import io.github.describeadmin.system.mapper.SysConfigMapper;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Optional;

/**
 * 系统参数配置管理。
 *
 * <p>{@link #getValue} 是给框架其余模块（如未来的通知渠道开关）直接调用的便捷方法，
 * 不必都经过 HTTP。读穿 {@link CacheProvider}，取舍与 {@link SysDictDataService} 相同。
 *
 * <p>{@link SysConfig#getConfigType()}（是否内置）只应由种子数据设置，不接受 API 调用方
 * 指定——{@link #save} 因此强制清空该字段；{@link #removeById} 拒绝删除内置参数。
 *
 * <p>{@code configKey} 唯一性在应用层校验，而非数据库唯一索引——逻辑删除下建唯一索引
 * 会导致删除后无法复用同名参数键，理由同 {@code SysUserService.username}（见
 * schema-rbac.sql 对应注释）。
 */
@Service
public class SysConfigService extends BaseService<SysConfigMapper, SysConfig> {

    private static final String CACHE_KEY_PREFIX = "sys:config:";
    private static final String BUILTIN = "Y";

    private final CacheProvider cacheProvider;
    private final FrameworkSystemProperties properties;

    public SysConfigService(CacheProvider cacheProvider, FrameworkSystemProperties properties) {
        this.cacheProvider = cacheProvider;
        this.properties = properties;
    }

    public String getValue(String configKey) {
        return getValue(configKey, null);
    }

    public String getValue(String configKey, String defaultValue) {
        String key = cacheKey(configKey);
        Optional<String> cached = cacheProvider.get(key, String.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        SysConfig config = getOne(new QueryWrapper<SysConfig>().eq("config_key", configKey), false);
        String value = config == null ? defaultValue : config.getConfigValue();
        if (value != null) {
            cacheProvider.put(key, value, properties.getConfig().getCacheTtl());
        }
        return value;
    }

    public void evictCache(String configKey) {
        cacheProvider.evict(cacheKey(configKey));
    }

    /**
     * 校验参数键在「未删除」范围内未被其他记录占用。
     *
     * <p>{@code selfId} 是当前正在创建/编辑的记录自身 id：新建时传 {@code null}
     * （不存在"自己"），编辑时传当前 id，排除"改别的字段但键名没变"这种误判。
     */
    private void assertConfigKeyAvailable(Long selfId, String configKey) {
        SysConfig exist = getOne(new QueryWrapper<SysConfig>().eq("config_key", configKey), false);
        if (exist != null && !exist.getId().equals(selfId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "参数键名已存在: " + configKey);
        }
    }

    /** 内置标记只能来自种子数据，API 新增的参数一律按自定义处理，忽略调用方传入的值。 */
    @Override
    public boolean save(SysConfig entity) {
        assertConfigKeyAvailable(null, entity.getConfigKey());
        entity.setConfigType(null);
        boolean result = super.save(entity);
        evictCache(entity.getConfigKey());
        return result;
    }

    @Override
    public boolean updateById(SysConfig entity) {
        if (entity.getConfigKey() != null) {
            assertConfigKeyAvailable(entity.getId(), entity.getConfigKey());
        }
        SysConfig existing = getById(entity.getId());
        boolean result = super.updateById(entity);
        if (existing != null) {
            evictCache(existing.getConfigKey());
        }
        if (entity.getConfigKey() != null) {
            evictCache(entity.getConfigKey());
        }
        return result;
    }

    /** 内置参数（{@code configType} = "Y"）不允许删除，防止业主误删框架依赖的默认参数。 */
    @Override
    public boolean removeById(Serializable id) {
        SysConfig existing = getById(id);
        if (existing != null && BUILTIN.equals(existing.getConfigType())) {
            throw new BizException(ResultCode.BAD_REQUEST, "内置参数不允许删除");
        }
        boolean result = super.removeById(id);
        if (existing != null) {
            evictCache(existing.getConfigKey());
        }
        return result;
    }

    private static String cacheKey(String configKey) {
        return CACHE_KEY_PREFIX + configKey;
    }
}
