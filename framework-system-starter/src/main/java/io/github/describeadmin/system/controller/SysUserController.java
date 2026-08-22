package io.github.describeadmin.system.controller;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.mybatis.api.BaseController;
import io.github.describeadmin.system.core.OperLog;
import io.github.describeadmin.system.entity.SysUser;
import io.github.describeadmin.system.mapper.SysUserMapper;
import io.github.describeadmin.system.service.SysUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户管理。
 *
 * <p>创建与改密走独立端点，不复用 BaseController 的通用 CRUD——
 * 密码必须经过哈希，通用端点会把实体字段原样落库，等于把明文写进数据库。
 */
@RestController
@RequestMapping("/api/system/user")
public class SysUserController extends BaseController<SysUserService, SysUserMapper, SysUser> {

    private final SysUserService service;

    public SysUserController(SysUserService service) {
        this.service = service;
    }

    @Override
    protected SysUserService getService() {
        return service;
    }

    /**
     * 显式禁用继承来的通用创建端点。
     *
     * <p>这是有意为之：BaseController 的 create 会把请求体直接写库，
     * 对于含密码字段的实体这是安全事故。宁可让调用方拿到明确的错误，
     * 也不要留一个"能用但会存明文密码"的入口。
     */
    @Override
    @PostMapping
    public Result<SysUser> create(@RequestBody SysUser entity) {
        throw new BizException(ResultCode.BAD_REQUEST,
                "创建用户请使用 POST /api/system/user/with-password");
    }

    @OperLog(module = "system:user", description = "创建用户")
    @PreAuthorize("hasAuthority('system:user:add')")
    @PostMapping("/with-password")
    public Result<SysUser> createWithPassword(@RequestBody Map<String, Object> body) {
        SysUser u = new SysUser();
        u.setUsername(asString(body.get("username")));
        u.setNickname(asString(body.get("nickname")));
        u.setMobile(asString(body.get("mobile")));
        u.setEmail(asString(body.get("email")));
        if (body.get("deptId") != null) {
            u.setDeptId(Long.valueOf(String.valueOf(body.get("deptId"))));
        }
        return Result.ok(service.createUser(u, asString(body.get("password")), asIdList(body.get("roleIds"))));
    }

    /**
     * 覆写通用编辑端点，补手机号/邮箱唯一性校验——BaseController.update() 没有这个钩子。
     *
     * <p>密码字段不在这里处理：编辑表单不会把密码哈希原样带回来，反序列化后
     * {@code entity.password} 必然是 null，而 {@link SysUser#getPassword()}
     * 已经用 {@code @TableField(updateStrategy = NOT_NULL)} 锁住，null 不会覆盖已有密码，
     * 不需要 controller 层再处理一遍。
     */
    @Override
    @OperLog(module = "system:user", description = "更新用户")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @PutMapping("/{id}")
    public Result<SysUser> update(@PathVariable Long id, @RequestBody SysUser entity) {
        service.assertMobileEmailAvailable(id, entity.getMobile(), entity.getEmail());
        entity.setId(id);
        if (!service.updateById(entity)) {
            throw new BizException(ResultCode.NOT_FOUND, "记录不存在或已被他人修改: " + id);
        }
        return Result.ok(service.getById(id));
    }

    @OperLog(module = "system:user", description = "重置密码")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @PutMapping("/{userId}/password")
    public Result<Void> resetPassword(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        service.resetPassword(userId, body.get("password"));
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('system:user:list')")
    @GetMapping("/{userId}/roles")
    public Result<List<Long>> roles(@PathVariable Long userId) {
        return Result.ok(service.roleIdsOf(userId));
    }

    /** 重新授予角色。整体覆盖而非增量修改。 */
    @OperLog(module = "system:user", description = "分配角色")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @PutMapping("/{userId}/roles")
    public Result<Void> assignRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        service.assignRoles(userId, roleIds);
        return Result.ok();
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /** JSON 数字反序列化为 Integer 还是 Long 取决于取值大小，统一收敛为 Long。 */
    private static List<Long> asIdList(Object v) {
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(java.util.Objects::nonNull)
                .map(o -> Long.valueOf(String.valueOf(o)))
                .toList();
    }
}
