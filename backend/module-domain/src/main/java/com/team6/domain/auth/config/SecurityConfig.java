package com.team6.domain.auth.config;

import com.team6.domain.auth.filter.JwtAuthenticationFilter;
import com.team6.domain.auth.provider.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 안씀
                .authorizeHttpRequests(auth -> auth
                        // 1. 누구나 접근 가능한 경로 (화이트리스트)
                        .requestMatchers("/auth/**", "/members/join").permitAll()
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
                );

        http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
