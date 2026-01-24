package com.tr1l.apiserver.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

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

    @Bean
    public OpenApiCustomizer securityApiCustomizer() {
        return openApi -> {
            // 1. JSON 로그인 API 정의 (jsonFilter 설정에 맞춤)
            Schema<?> loginSchema = new Schema<>()
                    .addProperty("email", new StringSchema().example("string"))
                    .addProperty("password", new StringSchema().example("string"));

            Operation loginOperation = new Operation()
                    .summary("JSON 로그인")
                    .description("JSON 형태로 아이디/비밀번호를 전송하여 세션을 생성합니다.")
                    .tags(List.of("Auth"))
                    .requestBody(new RequestBody().content(new Content().addMediaType("application/json",
                            new MediaType().schema(loginSchema))))
                    .responses(new ApiResponses()
                            .addApiResponse("200", new ApiResponse().description("로그인 성공"))
                            .addApiResponse("401", new ApiResponse().description("인증 실패")));

            // 2. 로그아웃 API 정의 (logoutUrl 설정에 맞춤)
            Operation logoutOperation = new Operation()
                    .summary("로그아웃")
                    .description("현재 세션을 무효화하고 쿠키를 삭제합니다.")
                    .tags(List.of("Auth"))
                    .responses(new ApiResponses()
                            .addApiResponse("200", new ApiResponse().description("로그아웃 성공")));

            // SecurityConfig에 설정된 실제 URL 주소로 등록
            openApi.getPaths().addPathItem("/api/auth/login", new PathItem().post(loginOperation));
            openApi.getPaths().addPathItem("/api/auth/logout", new PathItem().post(logoutOperation));
        };
    }
}