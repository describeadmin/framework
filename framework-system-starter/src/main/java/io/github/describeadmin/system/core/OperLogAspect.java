package io.github.describeadmin.system.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.describeadmin.mybatis.api.BaseController;
import io.github.describeadmin.security.api.LoginUser;
import io.github.describeadmin.system.entity.SysOperLog;
import io.github.describeadmin.system.service.SysOperLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 操作日志切面。
 *
 * <p>两条切入路径，与权限校验"框架托底通用路径 + 自定义路径显式声明"是同一个心智模型：
 * <ul>
 *   <li>{@link #aroundBaseControllerWrite}：{@code BaseController} 的
 *       create/update/delete 自动记录，业务方什么都不用做</li>
 *   <li>{@link #aroundAnnotated}：标了 {@link OperLog} 的自定义端点</li>
 * </ul>
 *
 * <p>覆写了 create/update/delete 的子类（如 {@code SysDeptController}）<b>不需要</b>
 * 额外标注——AspectJ 的 {@code execution(Type+.method(..))} 按方法签名匹配整个类型层级，
 * 覆写不会让它从"这是一次 BaseController.create() 执行"里消失。反过来说，
 * 这也是为什么这两条路径的 pointcut 必须互斥（后者只匹配自定义方法名），
 * 否则覆写方法会被重复记两条。
 *
 * <p>日志写入失败只记 {@code slf4j} 错误、绝不向上抛出——记录一次操作失败了，
 * 不该连带把真正的业务操作也拖下水。
 */
@Aspect
public class OperLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperLogAspect.class);

    /** 命中即整段替换为 {@code ***}，大小写不敏感、子串匹配。 */
    private static final List<String> SENSITIVE_KEY_FRAGMENTS =
            List.of("password", "pwd", "secret", "token");

    private static final int MAX_PARAM_LENGTH = 2000;
    private static final int MAX_ERROR_LENGTH = 2000;

    private final SysOperLogService operLogService;
    private final ObjectMapper objectMapper;

    public OperLogAspect(SysOperLogService operLogService, ObjectMapper objectMapper) {
        this.operLogService = operLogService;
        this.objectMapper = objectMapper;
    }

    @Around("execution(* io.github.describeadmin.mybatis.api.BaseController+.create(..)) || "
            + "execution(* io.github.describeadmin.mybatis.api.BaseController+.update(..)) || "
            + "execution(* io.github.describeadmin.mybatis.api.BaseController+.delete(..))")
    public Object aroundBaseControllerWrite(ProceedingJoinPoint joinPoint) throws Throwable {
        String module = permPrefixOf(joinPoint.getTarget());
        String description = module + " " + actionWord(joinPoint.getSignature().getName());
        return record(joinPoint, module, description);
    }

    @Around("@annotation(operLog)")
    public Object aroundAnnotated(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        return record(joinPoint, operLog.module(), operLog.description());
    }

    private Object record(ProceedingJoinPoint joinPoint, String module, String description) throws Throwable {
        long start = System.currentTimeMillis();
        SysOperLog entry = new SysOperLog();
        entry.setModule(module);
        entry.setDescription(description);
        fillRequestInfo(entry);
        fillOperator(entry);
        entry.setRequestParam(serializeArgs(joinPoint.getArgs()));

        try {
            Object result = joinPoint.proceed();
            entry.setStatus(SysOperLog.STATUS_SUCCESS);
            return result;
        } catch (Throwable ex) {
            entry.setStatus(SysOperLog.STATUS_FAIL);
            entry.setErrorMsg(truncate(ex.getMessage(), MAX_ERROR_LENGTH));
            throw ex;
        } finally {
            entry.setCostTime(System.currentTimeMillis() - start);
            entry.setCreateTime(LocalDateTime.now());
            persist(entry);
        }
    }

    private void persist(SysOperLog entry) {
        try {
            operLogService.record(entry);
        } catch (Exception e) {
            log.error("操作日志写入失败，不影响本次业务操作本身: module={}", entry.getModule(), e);
        }
    }

    private static String permPrefixOf(Object controller) {
        return controller instanceof BaseController<?, ?, ?> base ? base.permPrefix() : "unknown";
    }

    private static String actionWord(String methodName) {
        return switch (methodName) {
            case "create" -> "新增";
            case "update" -> "编辑";
            case "delete" -> "删除";
            default -> methodName;
        };
    }

    private void fillRequestInfo(SysOperLog entry) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }
        entry.setHttpMethod(request.getMethod());
        entry.setRequestUrl(request.getRequestURI());
        entry.setOperatorIp(clientIp(request));
    }

    private static HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        return attrs.getRequest();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void fillOperator(SysOperLog entry) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user) {
            entry.setOperatorId(user.getUserId());
            entry.setOperatorName(user.getUsername());
        }
    }

    /**
     * 序列化方法参数并脱敏，绝不因为序列化失败让整次操作日志记录失败。
     *
     * <p>包可见（非 {@code private}）：同包的 {@code OperLogAspectTest} 直接调用它，
     * 不必为了测这段纯粹的字符串拼装逻辑去反射私有方法或起 Spring 上下文。
     */
    String serializeArgs(Object[] args) {
        try {
            Object[] loggable = Arrays.stream(args)
                    .filter(arg -> !(arg instanceof HttpServletRequest)
                            && !(arg instanceof HttpServletResponse))
                    .toArray();
            JsonNode tree = objectMapper.valueToTree(loggable);
            redact(tree);
            return truncate(objectMapper.writeValueAsString(tree), MAX_PARAM_LENGTH);
        } catch (Exception e) {
            return "[参数序列化失败]";
        }
    }

    /** 递归脱敏：命中敏感 key 的字段整段替换，不递归进它的值——值本身就是要藏起来的东西。 */
    private void redact(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> sensitiveKeys = new ArrayList<>();
            obj.properties().forEach(field -> {
                if (isSensitiveKey(field.getKey())) {
                    sensitiveKeys.add(field.getKey());
                } else {
                    redact(field.getValue());
                }
            });
            sensitiveKeys.forEach(key -> obj.put(key, "***"));
        } else if (node.isArray()) {
            node.forEach(this::redact);
        }
    }

    private static boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEY_FRAGMENTS.stream().anyMatch(lower::contains);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
