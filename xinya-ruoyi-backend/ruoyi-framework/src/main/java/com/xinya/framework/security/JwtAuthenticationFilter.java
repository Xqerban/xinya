package com.xinya.framework.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinya.common.core.domain.R;
import com.xinya.common.security.JwtTokenUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * JWT 认证过滤器（替代原 JwtAuthInterceptor，迁移为 Spring Security 过滤器链）
 *
 * 白名单路径直接放行，受保护路径校验 Bearer Token。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final ObjectMapper objectMapper;

    private static final Set<String> WHITE_LIST_PREFIXES = Set.of(
            "/api/auth/login",
            "/api/auth/login/phone",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/robot",
            "/api/robot",
            "/api/sync",
            "/api/agent",
            "/api/pro",
            "/api/hopetree",
            "/api/education/contents",
            "/api/education/progress",
            "/api/clinical/stage",
            "/api/patients/",
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-resources",
            "/error"
    );

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // /internal/** 由 InternalApiKeyFilter 处理，此处跳过
        if (path.startsWith("/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 白名单直接放行
        if (isWhiteListed(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 机器人端子路径放行
        if (path.startsWith("/api/robot/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 只拦截 /api/** 路径
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");
        String token = extractBearerToken(authorization);
        if (token == null) {
            writeError(response, 401, "缺少 Authorization: Bearer <token> 请求头");
            return;
        }

        DecodedJWT jwt;
        try {
            jwt = jwtTokenUtil.verifyAccessToken(token);
        } catch (Exception e) {
            writeError(response, 401, "token 无效或已过期");
            return;
        }

        String role = jwt.getClaim("role").asString();
        if (path.startsWith("/api/admin/") && !"ADMIN".equals(role)) {
            writeError(response, 403, "无权访问该资源，需要 ADMIN 角色");
            return;
        }

        // 将用户信息存入请求属性，供 Controller 使用
        request.setAttribute("currentUserId", jwt.getSubject());
        request.setAttribute("currentUserRole", role);
        request.setAttribute("currentUsername", jwt.getClaim("username").asString());
        request.setAttribute("currentUserPhone", jwt.getClaim("phone").asString());

        filterChain.doFilter(request, response);
    }

    private boolean isWhiteListed(String path) {
        for (String prefix : WHITE_LIST_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String extractBearerToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        R<Void> body = R.fail(code, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
