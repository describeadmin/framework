package io.github.describeadmin.system.controller;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.PageQuery;
import io.github.describeadmin.common.api.PageResult;
import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.security.api.LoginUser;
import io.github.describeadmin.system.entity.SysMessage;
import io.github.describeadmin.system.service.SysMessageService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户的站内信。
 *
 * <p>与 {@code AuthController} 的自助端点同一模式：操作对象永远是当前登录用户，
 * <b>不挂 {@code @PreAuthorize}</b>，任何已登录账号都能调用，只能看到、改到自己的消息。
 *
 * <p>只有收件人侧的端点，<b>没有"发消息"的端点</b>——发送是业务代码通过
 * {@link io.github.describeadmin.system.api.MessageSender} 做的事，见该接口的类注释。
 *
 * <p>不继承 {@code BaseController}，因此 {@code OperLogAspect} 不会给铃铛轮询记操作日志。
 */
@RestController
@RequestMapping("/api/auth/messages")
public class SysMessageController {

    private final SysMessageService service;

    public SysMessageController(SysMessageService service) {
        this.service = service;
    }

    /** 顶栏铃铛轮询：未读数 + 最近几条。 */
    @GetMapping("/inbox")
    public Result<SysMessageService.Inbox> inbox() {
        return Result.ok(service.inboxOwn(currentUserId()));
    }

    /** 消息中心分页。{@code unread=true} 只看未读。 */
    @GetMapping
    public Result<PageResult<SysMessage>> page(PageQuery query,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean unread) {
        return Result.ok(service.pageOwn(currentUserId(), query, category, unread));
    }

    @PutMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        service.readOwn(currentUserId(), id);
        return Result.ok();
    }

    @PutMapping("/read-all")
    public Result<Integer> readAll() {
        return Result.ok(service.readAllOwn(currentUserId()));
    }

    /** 清空已读。未读消息保留，见 {@link SysMessageService#clearReadOwn(Long)}。 */
    @DeleteMapping("/read")
    public Result<Integer> clearRead() {
        return Result.ok(service.clearReadOwn(currentUserId()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        service.removeOwn(currentUserId(), id);
        return Result.ok();
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser user)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return user.getUserId();
    }
}
