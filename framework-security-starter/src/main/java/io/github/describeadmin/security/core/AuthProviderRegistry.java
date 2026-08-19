package io.github.describeadmin.security.core;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.security.api.AuthProvider;
import io.github.describeadmin.security.api.AuthRequest;
import io.github.describeadmin.security.api.LoginUser;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * {@link AuthProvider} 的收集与调度。
 *
 * <p>通过构造注入 {@code List<AuthProvider>} 收集当前 classpath 上所有已注册的实现——
 * 谁在 classpath 上、谁注册了 Bean，谁就自动生效，核心逻辑不需要知道任何具体实现的名字。
 *
 * <p>启动时校验 {@link AuthProvider#type()} 是否重复。重复会导致调度行为不确定，
 * 与其运行期随机命中一个，不如启动就失败。
 */
public class AuthProviderRegistry {

    private final List<AuthProvider> providers;

    public AuthProviderRegistry(List<AuthProvider> providers) {
        List<AuthProvider> sorted = providers.stream()
                .sorted(Comparator.comparingInt(AuthProvider::order))
                .toList();
        validateNoDuplicateType(sorted);
        this.providers = sorted;
    }

    private static void validateNoDuplicateType(List<AuthProvider> providers) {
        List<String> types = providers.stream().map(AuthProvider::type).filter(Objects::nonNull).toList();
        List<String> duplicates = types.stream()
                .filter(t -> types.indexOf(t) != types.lastIndexOf(t))
                .distinct()
                .toList();
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException(
                    "存在重复的 AuthProvider type: " + duplicates
                            + "。每种登录方式的 type 必须全局唯一，请检查引入的 framework-auth-* 插件。");
        }
    }

    /**
     * 当前项目启用的登录方式列表。
     *
     * <p>供 {@code /api/auth/providers} 暴露给前端，使登录页动态渲染而非硬编码按钮。
     */
    public List<String> availableTypes() {
        return providers.stream().map(AuthProvider::type).toList();
    }

    public LoginUser authenticate(AuthRequest request) {
        return providers.stream()
                .filter(p -> p.supports(request.getType()))
                .findFirst()
                .orElseThrow(() -> new BizException(
                        ResultCode.AUTH_PROVIDER_NOT_FOUND,
                        "不支持的登录方式: " + request.getType()))
                .authenticate(request);
    }
}
