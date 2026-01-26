package com.xinya.dtx.ai;

import com.xinya.dtx.config.AiClientConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * AI网关
 * 统一管理与大模型API的交互
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiGateway {
    
    private final WebClient aiWebClient;
    private final AiClientConfig config;
    
    /**
     * 调用OpenAI兼容的Chat Completion API
     */
    public Mono<String> chatCompletion(String systemPrompt, String userMessage) {
        if (!config.isEnabled()) {
            return Mono.just("AI服务未启用，返回默认回复。");
        }
        
        Map<String, Object> requestBody = Map.of(
            "model", config.getModel(),
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
            ),
            "temperature", 0.7,
            "max_tokens", 1000
        );
        
        return aiWebClient.post()
            .uri("/chat/completions")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .map(response -> {
                try {
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                        return (String) message.get("content");
                    }
                } catch (Exception e) {
                    log.error("解析AI响应失败", e);
                }
                return "抱歉，我暂时无法回答这个问题。";
            })
            .onErrorResume(e -> {
                log.error("AI调用失败", e);
                return Mono.just("抱歉，AI服务暂时不可用。");
            });
    }
}
