package com.lza.aethercare.common.security;

import com.lza.aethercare.tenant.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Bearer token 解析 filter：成功則 set SecurityContext，失敗 / 無 token 則放行給後續鏈處理。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            jwtService.parse(token).ifPresent(decoded -> {
                AppUserDetails principal = AppUserDetails.fromToken(
                        decoded.userId(), decoded.username(), decoded.roles(), decoded.tenantId());
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                // 設置 tenant context；下游 service 透過 TenantContext.get() 取值供 Hibernate filter 注入
                TenantContext.set(decoded.tenantId());
            });
        }
        try {
            chain.doFilter(request, response);
        } finally {
            // 確保 thread pool 不殘留 tenant id（避免下個 request 取到上一個 tenant）
            TenantContext.clear();
        }
    }
}
