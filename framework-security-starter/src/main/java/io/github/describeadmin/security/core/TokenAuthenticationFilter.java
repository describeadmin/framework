package io.github.describeadmin.security.core;

import io.github.describeadmin.security.api.LoginUser;
import io.github.describeadmin.security.api.TokenStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 {@code Authorization: Bearer <token>} 解析登录态并填入 SecurityContext。
 *
 * <p><b>解析失败不拦截</b>——只是不填充上下文，放行给后续的授权规则去决定返回 401 还是放过。
 * 把"没有身份"和"无权访问"分开处理，才能让 permit-all 的接口（登录页、健康检查）
 * 在带着一个过期令牌时也照常工作。
 */
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final TokenStore tokenStore;

    public TokenAuthenticationFilter(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    /** 从请求中取出裸令牌；没有或格式不对时返回 null。 */
    public static String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            tokenStore.resolve(token).ifPresent(user -> {
                var authentication = new UsernamePasswordAuthenticationToken(
                        user, token, authoritiesOf(user));
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        chain.doFilter(request, response);
    }

    /**
     * 角色与权限点一并转成 GrantedAuthority。
     *
     * <p>角色加 {@code ROLE_} 前缀以适配 {@code hasRole()}，权限点保持原样供
     * {@code hasAuthority("system:user:add")} 使用——这两套在 Spring Security 里是同一个集合，
     * 不加前缀区分会让角色名和权限码互相撞车。
     *
     * <p><b>空白项直接跳过</b>：{@link SimpleGrantedAuthority} 的构造函数对空串/空白
     * 直接抛 {@code IllegalArgumentException}。本方法在<b>每个带令牌的请求</b>上执行，
     * 一条空 {@code perm_code}（菜单管理里"权限标识"留空存成了空串）就会让该用户的
     * 所有已认证请求整体 500。授权数据里出现空白项是脏数据，不该由它决定整个会话
     * 能不能用——这里静默过滤，写入侧（{@code SysMenuService}）与查询侧
     * （{@code SysRelationMapper.selectPermCodesByUserId}）另有归一与过滤。
     */
    private static List<GrantedAuthority> authoritiesOf(LoginUser user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : user.getRoles()) {
            if (role != null && !role.isBlank()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
        }
        for (String permission : user.getPermissions()) {
            if (permission != null && !permission.isBlank()) {
                authorities.add(new SimpleGrantedAuthority(permission));
            }
        }
        return authorities;
    }
}
