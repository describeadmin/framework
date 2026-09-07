package io.github.describeadmin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.describeadmin.cache.api.UniqueGuard;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.mybatis.api.BaseService;
import io.github.describeadmin.system.entity.SysDictType;
import io.github.describeadmin.system.mapper.SysDictTypeMapper;
import org.springframework.stereotype.Service;

/**
 * 字典类型管理。数据本身没有缓存需求——变更频率低、被引用少，直接查表即可。
 *
 * <p>{@code dictType} 唯一性在应用层校验，而非数据库唯一索引——逻辑删除下建唯一索引
 * 会导致删除后无法复用同名字典类型，理由同 {@code SysUserService.username}（见
 * schema-rbac.sql 对应注释）。<b>并发唯一性由 {@link UniqueGuard} 的键级锁保证</b>：
 * 没有锁时两个并发请求会同时通过校验、各插一条。
 */
@Service
public class SysDictTypeService extends BaseService<SysDictTypeMapper, SysDictType> {

    private final UniqueGuard uniqueGuard;

    public SysDictTypeService(UniqueGuard uniqueGuard) {
        this.uniqueGuard = uniqueGuard;
    }

    /**
     * 校验字典类型在「未删除」范围内未被其他记录占用。
     *
     * <p>{@code selfId} 是当前正在创建/编辑的记录自身 id：新建时传 {@code null}
     * （不存在"自己"），编辑时传当前 id，排除"改别的字段但类型没变"这种误判。
     */
    private void assertDictTypeAvailable(Long selfId, String dictType) {
        SysDictType exist = getOne(new QueryWrapper<SysDictType>().eq("dict_type", dictType), false);
        if (exist != null && !exist.getId().equals(selfId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "字典类型已存在: " + dictType);
        }
    }

    @Override
    public boolean save(SysDictType entity) {
        return uniqueGuard.execute("sys:dict:type", entity.getDictType(), () -> {
            assertDictTypeAvailable(null, entity.getDictType());
            return super.save(entity);
        });
    }

    @Override
    public boolean updateById(SysDictType entity) {
        return uniqueGuard.execute("sys:dict:type", entity.getDictType(), () -> {
            if (entity.getDictType() != null) {
                assertDictTypeAvailable(entity.getId(), entity.getDictType());
            }
            return super.updateById(entity);
        });
    }
}
