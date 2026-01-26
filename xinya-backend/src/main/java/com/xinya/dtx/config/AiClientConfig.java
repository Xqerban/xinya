package com.xinya.dtx.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * AI客户端配置
 */
@Configuration
@ConfigurationProperties(prefix = "xinya.ai")
@Data
public class AiClientConfig {
    
    private boolean enabled = false;
    private String provider = "openai-compatible";
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey = "";
    private String model = "gpt-4";
    private int timeout = 30000;
    
    @Bean
    public WebClient aiWebClient() {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}
