package io.github.describeadmin.system.controller;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.PageQuery;
import io.github.describeadmin.common.api.PageResult;
import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.system.entity.SysOperLog;
import io.github.describeadmin.system.service.SysOperLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 操作日志查询与清理。
 *
 * <p>只追加、由 {@code OperLogAspect} 写入，没有"创建/编辑"语义，因此<b>不继承
 * {@code BaseController}</b>，权限点用 {@code @PreAuthorize} 显式声明——与
 * {@code SysOnlineController} 是同一类判断。
 */
@RestController
@RequestMapping("/api/system/oper-log")
public class SysOperLogController {

    private final SysOperLogService service;

    public SysOperLogController(SysOperLogService service) {
        this.service = service;
    }

    @PreAuthorize("hasAuthority('system:oper-log:list')")
    @GetMapping
    public Result<PageResult<SysOperLog>> list(PageQuery query,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return Result.ok(service.page(query, module, operatorName, status, start, end));
    }

    @PreAuthorize("hasAuthority('system:oper-log:remove')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        if (!service.removeById(id)) {
            throw new BizException(ResultCode.NOT_FOUND, "记录不存在: " + id);
        }
        return Result.ok();
    }

    /** 清空全部操作日志。复用删除的权限点，不为它单独开一个权限对象。 */
    @PreAuthorize("hasAuthority('system:oper-log:remove')")
    @DeleteMapping("/clean")
    public Result<Void> clean() {
        service.clean();
        return Result.ok();
    }
}
