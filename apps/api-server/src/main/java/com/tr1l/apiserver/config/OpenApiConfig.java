package com.tr1l.apiserver.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Server (JSON Session Auth)")
                        .version("v1.0")
                        .description("커스텀 필터를 이용한 JSON 세션 인증 서버입니다."));
    }
}