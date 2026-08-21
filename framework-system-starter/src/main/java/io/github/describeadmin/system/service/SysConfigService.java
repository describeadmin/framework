package io.github.describeadmin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.describeadmin.cache.api.CacheProvider;
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
 */
@Service
public class SysConfigService extends BaseService<SysConfigMapper, SysConfig> {

    private static final String CACHE_KEY_PREFIX = "sys:config:";

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

    @Override
    public boolean save(SysConfig entity) {
        boolean result = super.save(entity);
        evictCache(entity.getConfigKey());
        return result;
    }

    @Override
    public boolean updateById(SysConfig entity) {
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

    @Override
    public boolean removeById(Serializable id) {
        SysConfig existing = getById(id);
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
