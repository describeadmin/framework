package io.github.describeadmin.system.service;

import io.github.describeadmin.mybatis.api.BaseService;
import io.github.describeadmin.system.entity.SysDictType;
import io.github.describeadmin.system.mapper.SysDictTypeMapper;
import org.springframework.stereotype.Service;

/** 字典类型管理。数据本身没有缓存需求——变更频率低、被引用少，直接查表即可。 */
@Service
public class SysDictTypeService extends BaseService<SysDictTypeMapper, SysDictType> {
}
