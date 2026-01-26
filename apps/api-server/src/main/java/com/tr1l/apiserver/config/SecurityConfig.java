package com.tr1l.apiserver.config;

import com.tr1l.apiserver.auth.AdminUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AdminUserDetailsService adminUserDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(provider);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http,
                                                        AuthenticationManager authManager) throws Exception {

        // 세션 정보를 저장할 저장소 생성
        HttpSessionSecurityContextRepository repo = new HttpSessionSecurityContextRepository();

        JsonUsernamePasswordAuthenticationFilter jsonFilter = new JsonUsernamePasswordAuthenticationFilter();
        jsonFilter.setAuthenticationManager(authManager);
        jsonFilter.setFilterProcessesUrl("/api/auth/login");

        // 중요: 필터 내부에서도 이 저장소를 사용하도록 설정
        jsonFilter.setSecurityContextRepository(repo);

        jsonFilter.setAuthenticationSuccessHandler((req, res, auth) -> {
            res.setStatus(HttpServletResponse.SC_OK);
            res.setCharacterEncoding("UTF-8");
            res.getWriter().write("로그인 성공");
        });

        jsonFilter.setAuthenticationFailureHandler((req, res, exc) -> {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401로 응답하여 403과 구분
            res.setCharacterEncoding("UTF-8");
            res.getWriter().write("로그인 실패: " + exc.getMessage());
        });

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // [필수 추가] CORS 활성화
                .csrf(AbstractHttpConfigurer::disable)
                // 중요: 전체 보안 설정에도 해당 저장소 등록
                .securityContext(context -> context.securityContextRepository(repo))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/signup",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                )
                // addFilterAt 대신 addFilterBefore로 확실히 순서 보장
                .addFilterBefore(jsonFilter, UsernamePasswordAuthenticationFilter.class);

        http
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout") // 로그아웃을 수행할 URL
                        .logoutSuccessHandler((req, res, auth) -> {
                            res.setStatus(HttpServletResponse.SC_OK);
                            res.setCharacterEncoding("UTF-8");
                            res.getWriter().write("로그아웃 성공");
                        })
                        .invalidateHttpSession(true) // 서버 세션 무효화
                        .deleteCookies("JSESSIONID") // 클라이언트 쿠키 삭제
                );

        return http.build();
    }

    // CORS 설정을 위한 Bean 추가
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 프론트엔드 배포 주소 허용
        configuration.addAllowedOrigin("https://main.d27n7dug6je8px.amplifyapp.com");
        // 로컬 테스트가 필요하다면 아래 주석 해제
        // configuration.addAllowedOrigin("http://localhost:3000");

        configuration.addAllowedMethod("*"); // 모든 HTTP 메서드 허용 (GET, POST, PUT 등)
        configuration.addAllowedHeader("*"); // 모든 헤더 허용
        configuration.setAllowCredentials(true); // 쿠키/세션 인증 허용 (JSESSIONID를 위해 필수)
        configuration.setMaxAge(3600L); // 프리플라이트 캐싱 시간

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}