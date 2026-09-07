package io.github.describeadmin.cache.api;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 键级唯一性保护：把"check 唯一 → 写入"这两步包进同一把锁。
 *
 * <p><b>解决什么问题</b>：本框架的表因逻辑删除不建唯一索引（删除后要能复用
 * 同名值），唯一性全靠应用层"先查后插"。两步之间没有锁时，并发请求会同时
 * 通过检查、各插一条——校验形同虚设。本组件把这两步串行化：同一值的并发
 * 写入，后到的直接收到"操作冲突"。
 *
 * <p><b>锁的是值，不是表</b>：键为 {@code {锁前缀}unique:{namespace}:{value}}，
 * 不同值互不阻塞。update 场景锁"目标新值"，天然正确。
 *
 * <p><b>为什么用注解做不到</b>：像"创建用户"要同时保护 username、mobile、
 * email 三个字段，一个注解只有一个 key。多字段唯一性是编程式场景，
 * 嵌套 {@code execute} 即可——各键不同且 tryLock 不等待，无死锁可能。
 *
 * <p><b>⚠️ 不可重入（使用方必读）</b>：底层 {@link LockOperations} 刻意不做重入
 * （见其 javadoc）。同一线程在 action 内再次 {@code execute} <b>同一个键</b>，
 * 内层 {@code tryLock} 必然拿不到锁，直接抛"操作冲突"——<b>不是并发，是必现</b>，
 * 且错误信息与真正的并发冲突一字不差，极难指向真因。因此：
 * <ul>
 *   <li>嵌套只允许用于<b>不同键</b>（username / mobile / email 各一把）</li>
 *   <li>"加锁的入口方法"与"锁内调用的校验方法"必须二选一持锁。校验方法应写成
 *       不加锁的纯校验（并在 javadoc 里写明"调用方须已持锁"），由入口统一持锁——
 *       两处都持同一把键，调用链一接起来就 100% 失败</li>
 * </ul>
 *
 * <p><b>与事务的顺序（使用方必读）</b>：execute 返回即释放锁。若外层方法带
 * {@code @Transactional}，会出现"锁已释放、事务未提交"的窗口，后到的请求
 * 读不到前一个未提交的插入——<b>防重失效</b>。带事务的调用方必须把事务
 * 放进 action 里（如 {@code TransactionTemplate}），保证 锁 → 事务 → 提交 →
 * 释放锁 的顺序。无事务（语句自动提交）的方法直接包 execute 即可。
 *
 * <p>value 为 null 或空白时不加锁直接执行——空值没有唯一性冲突可言，
 * 与 {@code SysUserService.assertMobileEmailAvailable} 只校验非空字段的
 * 既有行为一致。
 */
public class UniqueGuard {

    /** 锁的存活时间。action 超过它仍未完成时锁会提前开放（见 LockOperations 的取舍说明）。 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    private final LockOperations lockOperations;

    public UniqueGuard(LockOperations lockOperations) {
        this.lockOperations = lockOperations;
    }

    /**
     * 在 {@code value} 的键级锁保护下执行 {@code action}。
     *
     * @param namespace 值的业务命名空间，如 {@code user:username}、{@code sys:config:key}
     * @param value     要保护唯一性的值；null/空白时不加锁
     * @param action    检查与写入，锁在其执行期间保持
     * @throws BizException 锁被并发占用（"操作冲突"）；action 抛出的异常原样上抛，锁自动释放
     */
    public <T> T execute(String namespace, Object value, Supplier<T> action) {
        if (value == null || String.valueOf(value).isBlank()) {
            return action.get();
        }
        Optional<LockHandle> handle = lockOperations.tryLock(lockKey(namespace, value), LOCK_TTL);
        if (handle.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "操作冲突，请稍后重试");
        }
        try (LockHandle lock = handle.get()) {
            return action.get();
        }
    }

    /** {@link #execute(String, Object, Supplier)} 的无返回值形态。 */
    public void execute(String namespace, Object value, Runnable action) {
        execute(namespace, value, () -> {
            action.run();
            return null;
        });
    }

    private String lockKey(String namespace, Object value) {
        return "unique:" + namespace + ":" + value;
    }
}
