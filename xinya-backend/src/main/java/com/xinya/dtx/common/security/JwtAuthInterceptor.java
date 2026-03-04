package com.xinya.dtx.common.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinya.dtx.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 简单基于 JWT 的鉴权拦截器：
 * - 路由白名单直接放行（登录、注册、机器人相关、文档等）
 * - 其余 /api/** 需要携带 Bearer access token
 * - /api/admin/** 仅 ADMIN 角色可访问
 */
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    /**
     * 完全公开的接口前缀（不做 JWT 校验）
     * 主要包含：
     * - 认证入口：登录 / 注册 / 刷新 / 机器人绑定
     * - 机器人 / 患者端使用的业务接口
     * - 文档与错误页
     */
    private static final Set<String> WHITE_LIST_PREFIXES = Set.of(
            // 认证相关（用户名/手机号登录、注册、刷新、机器人绑定）
            "/api/auth/login",
            "/api/auth/login/phone",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/robot",

            // 机器人设备与离线同步
            "/api/robot",
            "/api/sync",

            // 机器人 / 患者常用业务接口
            "/api/agent",              // 对话、推荐问题、历史
            "/api/pro",                // PRO 问卷：获取题目、提交、历史
            "/api/hopetree",           // 希望之树：状态 / 成长 / 历史
            "/api/education/contents", // 宣教内容列表
            "/api/education/progress", // 宣教观看进度上报
            "/api/clinical/stage",     // 当前临床阶段查询
            "/api/patients/",          // 患者详情（机器人端获取患者信息）

            // 文档相关
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-resources",

            // 全局错误页
            "/error"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // 仅拦截 HandlerMethod，静态资源等直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 白名单直接放行
        if (isWhiteListed(path)) {
            return true;
        }

        // 仅保护 /api/** 路径
        if (!path.startsWith("/api/")) {
            return true;
        }

        // 机器人端接口暂不使用用户 JWT 鉴权
        if (path.startsWith("/api/robot/")) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        String token = extractToken(authorization);
        if (token == null) {
            writeUnauthorized(response, "缺少 Authorization: Bearer <token> 请求头");
            return false;
        }

        DecodedJWT jwt;
        try {
            jwt = jwtUtil.verifyAccessToken(token);
        } catch (Exception e) {
            writeUnauthorized(response, "token 无效或已过期");
            return false;
        }

        String role = jwt.getClaim("role").asString();
        if (path.startsWith("/api/admin/") && !"ADMIN".equals(role)) {
            writeForbidden(response, "无权访问该资源，需要 ADMIN 角色");
            return false;
        }

        // 将当前用户基本信息放入请求属性，后续如有需要可在 Controller 中读取
        request.setAttribute("currentUserId", jwt.getSubject());
        request.setAttribute("currentUserRole", role);
        request.setAttribute("currentUsername", jwt.getClaim("username").asString());
        request.setAttribute("currentUserPhone", jwt.getClaim("phone").asString());

        return true;
    }

    private boolean isWhiteListed(String path) {
        for (String prefix : WHITE_LIST_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        writeError(response, 401, message);
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        writeError(response, 403, message);
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.error(code, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

