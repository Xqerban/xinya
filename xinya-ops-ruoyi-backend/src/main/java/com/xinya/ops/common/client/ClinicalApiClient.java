package com.xinya.ops.common.client;

import com.xinya.ops.common.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * 调用 xinya-ruoyi-backend /internal/** 接口的 WebClient 封装
 * 所有请求自动携带 X-Internal-Key 请求头
 */
@Slf4j
@Component
public class ClinicalApiClient {

    private final WebClient webClient;

    public ClinicalApiClient(
            WebClient.Builder builder,
            @Value("${xinya.clinical.base-url:http://localhost:8080}") String baseUrl,
            @Value("${xinya.clinical.internal-key:dev-internal-key}") String internalKey
    ) {
        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Key", internalKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public <T, Resp> R<Resp> post(String path, T body, ParameterizedTypeReference<R<Resp>> responseType) {
        try {
            return webClient.post()
                    .uri(path)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("POST {} failed: {} {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("调用 clinical 内部接口失败: " + e.getMessage(), e);
        }
    }

    public <T, Resp> R<Resp> put(String path, T body, ParameterizedTypeReference<R<Resp>> responseType) {
        try {
            return webClient.put()
                    .uri(path)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("PUT {} failed: {} {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("调用 clinical 内部接口失败: " + e.getMessage(), e);
        }
    }

    public <Resp> R<Resp> delete(String path, ParameterizedTypeReference<R<Resp>> responseType) {
        try {
            return webClient.delete()
                    .uri(path)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("DELETE {} failed: {} {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("调用 clinical 内部接口失败: " + e.getMessage(), e);
        }
    }

    public <Resp> R<Resp> get(String path, ParameterizedTypeReference<R<Resp>> responseType) {
        try {
            return webClient.get()
                    .uri(path)
                    .retrieve()
                    .bodyToMono(responseType)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("GET {} failed: {} {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("调用 clinical 内部接口失败: " + e.getMessage(), e);
        }
    }
}
