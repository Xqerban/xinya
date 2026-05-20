package com.xinya.framework.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinya.common.core.domain.R;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 内部服务间 API 鉴权过滤器
 * 所有 /internal/** 路径必须携带正确的 X-Internal-Key 请求头
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final String internalApiKey;
    private final ObjectMapper objectMapper;

    public InternalApiKeyFilter(
            @Value("${xinya.internal.api-key:dev-internal-key}") String internalApiKey,
            ObjectMapper objectMapper) {
        this.internalApiKey = internalApiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getHeader("X-Internal-Key");
        if (!internalApiKey.equals(key)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            R<Void> body = R.fail(401, "无效的内部服务密钥");
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
