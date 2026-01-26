package com.xinya.dtx.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger/OpenAPI 配置
 */
@Configuration
public class SwaggerConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("心芽DTx API文档")
                .version("1.0.0")
                .description("骨髓移植隔离病房数字疗法系统后端API接口文档")
                .contact(new Contact()
                    .name("Xinya DTx Team")
                    .email("support@xinya.com"))
                .license(new License()
                    .name("Proprietary")
                    .url("https://xinya.com/license")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("开发环境"),
                new Server().url("https://api.xinya.com").description("生产环境")
            ));
    }
}
