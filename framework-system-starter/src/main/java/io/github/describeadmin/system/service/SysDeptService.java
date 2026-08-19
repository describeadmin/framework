package io.github.describeadmin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.describeadmin.mybatis.api.BaseService;
import io.github.describeadmin.system.core.TreeBuilder;
import io.github.describeadmin.system.entity.SysDept;
import io.github.describeadmin.system.mapper.SysDeptMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 部门管理。 */
@Service
public class SysDeptService extends BaseService<SysDeptMapper, SysDept> {

    /** 部门树。 */
    public List<SysDept> tree() {
        List<SysDept> all = list(new QueryWrapper<SysDept>().orderByAsc("sort"));
        return TreeBuilder.build(all, SysDept::getId, SysDept::getParentId, SysDept::getChildren);
    }
}
