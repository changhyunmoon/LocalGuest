package com.team6.apiserver.auth.config;

import com.team6.apiserver.auth.filter.JwtAuthenticationFilter;
import com.team6.apiserver.auth.oauth.CustomOauth2UserService;
import com.team6.apiserver.auth.oauth.OAuth2SuccessHandler;
import com.team6.domain.auth.provider.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomOauth2UserService customOauth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final RedisTemplate<String, String> redisTemplate;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 안씀
                .authorizeHttpRequests(auth -> auth

                        //SSE, WebSocket  접근 허용
                        .requestMatchers("/notifications/subscribe").permitAll()
                        .requestMatchers("/ws-stomp/**").permitAll()

                        // 1. 누구나 접근 가능한 경로 (화이트리스트)
                        .requestMatchers("/auth/**", "/members/join").permitAll()
                        // 인증 인가 경로는 비로그인 유저도 접근 가능
                        .requestMatchers("/oauth2/**").permitAll()
                        .requestMatchers("/login/**").permitAll()
                        .requestMatchers("/guides/**").permitAll()

                        // Swagger UI + OpenAPI (springdoc). context-path 사용 시 환경에 따라 둘 다 허용
                        .requestMatchers(
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api/v3/api-docs",
                                "/api/v3/api-docs/**",
                                "/api/swagger-ui/**",
                                "/api/swagger-ui.html"
                        ).permitAll()

                        // 2. 리뷰 조회는 비로그인 유저도 가능
                        .requestMatchers(HttpMethod.GET, "/reviews/**").permitAll()

                        // 3. 그 외 (리뷰 등록, 채팅, 마이페이지 등)는 무조건 로그인 필요
                        .anyRequest().authenticated()
                )
                // OAuth2 로그인 설정 추가
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo ->
                                userInfo.userService(customOauth2UserService)) // 사용자 정보 처리
                        .successHandler(oAuth2SuccessHandler)                   // 성공 시 JWR 발급
                );


        http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, redisTemplate), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // CORS 세부 설정 Bean 추가
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();

        configuration.addAllowedOrigin("http://localhost:5173"); // 리액트 주소
        configuration.addAllowedMethod("*"); // 모든 HTTP 메서드 허용 (GET, POST, OPTIONS 등)
        configuration.addAllowedHeader("*"); // 모든 헤더 허용
        configuration.setAllowCredentials(true); // 쿠키/인증 정보 포함 허용

        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // 모든 경로에 적용
        return source;
    }

    // 배포시 헬스채크용
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
                .requestMatchers("/actuator/**", "/api/actuator/**")
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/api/v3/api-docs/**", "/api/swagger-ui/**");
    }
}
