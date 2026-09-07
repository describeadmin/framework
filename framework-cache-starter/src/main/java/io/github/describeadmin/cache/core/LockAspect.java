package io.github.describeadmin.cache.core;

import io.github.describeadmin.cache.api.DistributedLock;
import io.github.describeadmin.cache.api.LockHandle;
import io.github.describeadmin.cache.api.LockOperations;
import io.github.describeadmin.cache.api.LockStrategy;
import io.github.describeadmin.cache.autoconfigure.FrameworkLockProperties;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link DistributedLock} 的切面：解析 SpEL 键 → 按策略加锁 → 执行 → 释放。
 *
 * <p><b>@Order 的三层考量（1000 不是随手取的）</b>：
 * <ol>
 *   <li><b>必须在事务拦截器之前（更小的 order）</b>——这是本切面存在的底线：
 *       获取锁 → 开事务 → 提交 → 释放锁。顺序反了会出现"锁释放了事务未提交"
 *       的窗口，后到的请求读不到前一个未提交的写入，防重失效。
 *       事务拦截器默认 order 是 {@code LOWEST_PRECEDENCE}，1000 在它之前</li>
 *   <li><b>在方法级安全（@PreAuthorize 等，Spring Security 的拦截器 order ≤ 500）
 *       之后</b>——未通过鉴权的调用不该去碰锁：既是防无意义开销，
 *       也防未授权者通过高频请求制造锁争用</li>
 *   <li>业务方把事务拦截器 order 显式调到 1000 以下属于自担的显式覆盖</li>
 * </ol>
 */
@Aspect
@Order(1000)
public class LockAspect {

    private final LockOperations lockOperations;
    private final FrameworkLockProperties properties;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /** 表达式解析开销不小且注解键是固定集合，解析一次复用。 */
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    public LockAspect(LockOperations lockOperations, FrameworkLockProperties properties) {
        this.lockOperations = lockOperations;
        this.properties = properties;
    }

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String key = resolveKey(distributedLock, joinPoint);
        LockStrategy strategy = effectiveStrategy(distributedLock);
        Duration ttl = Duration.ofSeconds(distributedLock.ttl());

        Optional<LockHandle> handle = strategy == LockStrategy.FAIL_FAST
                ? lockOperations.tryLock(key, ttl)
                : lockOperations.lock(key, ttl, Duration.ofSeconds(distributedLock.waitTimeout()));
        if (handle.isEmpty()) {
            // FAIL_FAST：拿不到立即报错；WAIT：等待超时。文案区分开，排查时不用猜是哪种
            throw new BizException(ResultCode.BAD_REQUEST, strategy == LockStrategy.FAIL_FAST
                    ? "操作处理中，请稍后重试"
                    : "获取锁超时，请稍后重试");
        }
        try (LockHandle lock = handle.get()) {
            return joinPoint.proceed();
        }
    }

    /**
     * 最终生效策略：注解显式指定优先，DEFAULT 跟随全局配置。
     * 每次调用时解析——配置调整（如配置中心热更）能即时生效。
     */
    private LockStrategy effectiveStrategy(DistributedLock distributedLock) {
        return distributedLock.strategy() == LockStrategy.DEFAULT
                ? properties.getDefaultStrategy()
                : distributedLock.strategy();
    }

    private String resolveKey(DistributedLock distributedLock, ProceedingJoinPoint joinPoint) {
        String keyExpression = distributedLock.key();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        if (keyExpression == null || keyExpression.isBlank()) {
            // 缺省键 = 全限定类名.方法名：对定时任务正是想要的（一个任务一把全局键），
            // 对接口则要求调用方显式写 SpEL——缺省键会退化成"整个接口全局串行"，
            // 那是误用的味道，宁可让缺省只在定时任务场景被用。
            //
            // 用 getName() 而不是 getSimpleName()：后者会让不同包下的同名类共用一把锁
            // （com.a.SyncJob#run 与 com.b.SyncJob#run 都得到 "SyncJob.run"），
            // 而缺省键的主场景恰恰是定时任务——FAIL_FAST 下两个任务会互相把对方静默跳过，
            // 没有任何日志指向"键撞了"。键长由 LockOperations 的实现负责截断，不必在这里省字符
            return signature.getDeclaringType().getName() + "." + signature.getName();
        }
        Expression expression = expressionCache.computeIfAbsent(keyExpression, parser::parseExpression);
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                null, signature.getMethod(), joinPoint.getArgs(), parameterNameDiscoverer);
        Object value = expression.getValue(context);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "锁键表达式结果为空: " + keyExpression);
        }
        return String.valueOf(value);
    }
}
