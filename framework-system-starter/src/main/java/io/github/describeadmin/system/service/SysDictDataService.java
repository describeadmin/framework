package io.github.describeadmin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.describeadmin.cache.api.CacheProvider;
import io.github.describeadmin.mybatis.api.BaseService;
import io.github.describeadmin.system.autoconfigure.FrameworkSystemProperties;
import io.github.describeadmin.system.entity.SysDictData;
import io.github.describeadmin.system.mapper.SysDictDataMapper;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * 字典数据管理。
 *
 * <p>{@link #listByType} 读穿 {@link CacheProvider}：字典数据读多写少，每次打开一个
 * 带下拉框的表单都会查，直接查库没必要。写操作（{@link #evictCache}，由
 * {@code SysDictDataController} 在 create/update/delete 后调用）主动失效对应缓存，
 * TTL 只是双保险。
 */
@Service
public class SysDictDataService extends BaseService<SysDictDataMapper, SysDictData> {

    private static final String CACHE_KEY_PREFIX = "sys:dict:data:";

    private final CacheProvider cacheProvider;
    private final FrameworkSystemProperties properties;

    public SysDictDataService(CacheProvider cacheProvider, FrameworkSystemProperties properties) {
        this.cacheProvider = cacheProvider;
        this.properties = properties;
    }

    @SuppressWarnings("unchecked")
    public List<SysDictData> listByType(String dictType) {
        String key = cacheKey(dictType);
        Optional<List> cached = cacheProvider.get(key, List.class);
        if (cached.isPresent()) {
            return (List<SysDictData>) cached.get();
        }
        List<SysDictData> data = list(new QueryWrapper<SysDictData>()
                .eq("dict_type", dictType)
                .eq("status", 1)
                .orderByAsc("sort"));
        cacheProvider.put(key, data, properties.getDict().getCacheTtl());
        return data;
    }

    public void evictCache(String dictType) {
        cacheProvider.evict(cacheKey(dictType));
    }

    /**
     * 三个写方法都覆写成"先执行、再让对应 dictType 的缓存失效"——
     * 放在 Service 层而不是 Controller 层，任何调用方（HTTP、框架内部代码）都能受益，
     * 不必依赖每个调用方自己记得清缓存。
     */
    @Override
    public boolean save(SysDictData entity) {
        boolean result = super.save(entity);
        evictCache(entity.getDictType());
        return result;
    }

    @Override
    public boolean updateById(SysDictData entity) {
        // 更新前的 dictType 可能被改掉了，新旧两个都要失效
        SysDictData existing = getById(entity.getId());
        boolean result = super.updateById(entity);
        if (existing != null) {
            evictCache(existing.getDictType());
        }
        if (entity.getDictType() != null) {
            evictCache(entity.getDictType());
        }
        return result;
    }

    @Override
    public boolean removeById(Serializable id) {
        SysDictData existing = getById(id);
        boolean result = super.removeById(id);
        if (existing != null) {
            evictCache(existing.getDictType());
        }
        return result;
    }

    private static String cacheKey(String dictType) {
        return CACHE_KEY_PREFIX + dictType;
    }
}
