package com.xinya.ops.common.client;

import com.xinya.ops.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * 调用 xinya-backend /internal/** 接口的 WebClient 封装
 * 所有请求自动携带 X-Internal-Key 请求头
 */
@Slf4j
@Component
public class ClinicalApiClient {

    private final WebClient webClient;

    public ClinicalApiClient(
            WebClient.Builder builder,
            @Value("${xinya.clinical.base-url:http://localhost:8081}") String baseUrl,
            @Value("${xinya.clinical.internal-key:dev-internal-key}") String internalKey
    ) {
        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Key", internalKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * POST 请求
     */
    public <T, R> ApiResponse<R> post(String path, T body, ParameterizedTypeReference<ApiResponse<R>> responseType) {
        try {
            return webClient.post()
                    .uri(path)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("ClinicalApiClient POST {} failed: {} {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("调用 clinical 内部接口失败: " + e.getMessage(), e);
        }
    }

    /**
     * PUT 请求
     */
    public <T, R> ApiResponse<R> put(String path, T body, ParameterizedTypeReference<ApiResponse<R>> responseType) {
        try {
            return webClient.put()
                    .uri(path)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("ClinicalApiClient PUT {} failed: {} {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("调用 clinical 内部接口失败: " + e.getMessage(), e);
        }
    }

    /**
     * DELETE 请求
     */
    public <R> ApiResponse<R> delete(String path, ParameterizedTypeReference<ApiResponse<R>> responseType) {
        try {
            return webClient.delete()
                    .uri(path)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("ClinicalApiClient DELETE {} failed: {} {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("调用 clinical 内部接口失败: " + e.getMessage(), e);
        }
    }

    /**
     * GET 请求
     */
    public <R> ApiResponse<R> get(String path, ParameterizedTypeReference<ApiResponse<R>> responseType) {
        try {
            return webClient.get()
                    .uri(path)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("ClinicalApiClient GET {} failed: {} {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("调用 clinical 内部接口失败: " + e.getMessage(), e);
        }
    }
}
