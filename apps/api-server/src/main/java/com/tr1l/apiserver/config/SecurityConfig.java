package com.tr1l.apiserver.config;

import com.tr1l.apiserver.auth.AdminUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
}