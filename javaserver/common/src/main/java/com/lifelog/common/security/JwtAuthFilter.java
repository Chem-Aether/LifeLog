package com.lifelog.common.security;

import com.lifelog.common.config.JwtConfig;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 在请求到达控制器前校验 JWT；接口文档和预检请求不要求令牌。 */
@Component
@ConditionalOnProperty(name = "jwt.auth.enabled", havingValue = "true")
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtConfig jwtConfig;

    /**
     * 判断请求是否属于无需 JWT 校验的 API 文档路径。
     * @param request 当前 HTTP 请求
     * @return 无需执行认证过滤时返回 {@code true}
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals("/swagger-ui.html")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs");
    }

    /**
     * 校验请求头中的 JWT，通过后继续过滤链。
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param chain Servlet 过滤器链
     * @throws ServletException 过滤器处理失败时抛出
     * @throws IOException 请求或响应读写失败时抛出
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String token = request.getHeader("token");
        if (token == null || token.isBlank()) {
            unauthorized(response);
            return;
        }
        try {
            JWT.require(Algorithm.HMAC256(jwtConfig.getSecret())).build().verify(token);
            chain.doFilter(request, response);
        } catch (JWTVerificationException exception) {
            unauthorized(response);
        }
    }

    /**
     * 写入统一的未授权 JSON 响应。
     * @param response 当前 HTTP 响应
     * @throws IOException 响应写入失败时抛出
     */
    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"msg\":\"请先登录或登录已过期\"}");
    }
}
