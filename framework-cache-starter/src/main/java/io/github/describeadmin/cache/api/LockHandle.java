package io.github.describeadmin.cache.api;

/**
 * 锁句柄：持有者与锁之间的租约。
 *
 * <p>由 {@link LockOperations} 在加锁成功时返回，调用方在业务完成后通过
 * {@link #close()} 释放。<b>推荐写法是 try-with-resources</b>，漏掉 close
 * 的窗口最小——不释放也不会永久死锁，锁会在 TTL 到期后自动开放，
 * 但那之前所有对该键的并发请求都会被挡住。
 *
 * <p><b>close 的语义（两个实现都必须遵守）</b>：
 * <ul>
 *   <li><b>校验持有者身份后才删除</b>：每个句柄内部持有一把随机 token，
 *       释放时先比对存储里的 token 再删。若 TTL 已过期、锁已被下一个请求
 *       抢走，本句柄的 close 是无害的空操作——<b>绝不能误删别人的锁</b></li>
 *   <li><b>幂等</b>：重复 close 安全，第二次起为空操作</li>
 *   <li><b>不抛异常</b>：释放发生在 finally 语义下，抛异常会掩盖业务结果。
 *       释放失败时实现方自行记录日志，锁交由 TTL 兜底</li>
 * </ul>
 *
 * <p>本接口位于 {@code api} 包下，属于兼容性承诺范围。
 */
public interface LockHandle extends AutoCloseable {

    /** 释放锁。见类注释：幂等、token 安全、不抛异常。 */
    @Override
    void close();
}
