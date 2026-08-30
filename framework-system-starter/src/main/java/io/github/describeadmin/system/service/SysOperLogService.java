package io.github.describeadmin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.describeadmin.common.api.PageQuery;
import io.github.describeadmin.common.api.PageResult;
import io.github.describeadmin.system.entity.SysOperLog;
import io.github.describeadmin.system.mapper.SysOperLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 操作日志管理。
 *
 * <p><b>不继承 {@code BaseService}</b>：那个泛型基类要求
 * {@code T extends BaseEntity}，而 {@link SysOperLog} 刻意不继承
 * {@code BaseEntity}（见该类的类注释）。分页逻辑照抄 {@code BaseService.page()}——
 * 就三行，没必要为了复用这三行去改动泛型约束影响其他一切业务实体。
 */
@Service
public class SysOperLogService {

    private final SysOperLogMapper mapper;

    public SysOperLogService(SysOperLogMapper mapper) {
        this.mapper = mapper;
    }

    /** 由 {@code OperLogAspect} 调用，落一条日志。 */
    public void record(SysOperLog log) {
        mapper.insert(log);
    }

    public PageResult<SysOperLog> page(PageQuery query, String module, String operatorName,
                                       Integer status, LocalDateTime start, LocalDateTime end) {
        QueryWrapper<SysOperLog> wrapper = new QueryWrapper<SysOperLog>()
                .eq(module != null && !module.isBlank(), "module", module)
                .like(operatorName != null && !operatorName.isBlank(), "operator_name", operatorName)
                .eq(status != null, "status", status)
                .ge(start != null, "create_time", start)
                .le(end != null, "create_time", end)
                .orderByDesc("create_time");
        Page<SysOperLog> page = Page.of(query.getCurrent(), query.getSize());
        Page<SysOperLog> result = mapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(),
                result.getCurrent(), result.getSize());
    }

    public boolean removeById(Long id) {
        return mapper.deleteById(id) > 0;
    }

    /** 清空全部操作日志。 */
    public void clean() {
        mapper.deleteAll();
    }
}
