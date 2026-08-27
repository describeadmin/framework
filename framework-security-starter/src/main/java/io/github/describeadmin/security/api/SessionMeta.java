package io.github.describeadmin.security.api;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一次登录的来源信息：客户端 IP 与设备描述。
 *
 * <p>由 Web 层在登录时从 {@code HttpServletRequest} 提取后传给 {@link TokenStore}，
 * 存进会话，供"在线用户"页展示（见 {@link ActiveSession}）。两个字段都可能为
 * {@code null}——命令行调用、测试，或拿不到请求上下文时。
 *
 * <p>为什么是独立的值对象而不是塞进 {@link LoginUser}：{@code LoginUser} 是认证主体，
 * 承载的是"这个人是谁、能做什么"，IP/设备是这次连接的传输层属性，两者生命周期与关注点
 * 都不同。分开也让 {@link TokenStore} 的实现可以选择性忽略来源信息（默认实现就忽略）。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。
 */
public final class SessionMeta implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 不携带任何来源信息的空实例，供不关心来源的调用方使用，避免到处传 {@code null}。 */
    public static final SessionMeta EMPTY = new SessionMeta(null, null);

    /** 客户端 IP，尽力而为地穿透反向代理取真实来源，取不到时为 {@code null}。 */
    private final String ip;

    /**
     * 设备描述，如 {@code "Chrome · Windows"}。由 Web 层对 {@code User-Agent} 做
     * 轻量启发式解析得到，不追求精确——只是给管理员一个"这是从哪个端登录的"的大致印象。
     * 解析不出来时为 {@code null}。
     */
    private final String device;

    public SessionMeta(String ip, String device) {
        this.ip = ip;
        this.device = device;
    }

    public String getIp() {
        return ip;
    }

    public String getDevice() {
        return device;
    }
}
