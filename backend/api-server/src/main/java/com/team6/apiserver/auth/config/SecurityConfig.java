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
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomOauth2UserService customOauth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 안씀
                .authorizeHttpRequests(auth -> auth
                        // 1. 누구나 접근 가능한 경로 (화이트리스트)
                        .requestMatchers("/auth/**", "/members/join").permitAll()
                        // 인증 인가 경로는 비로그인 유저도 접근 가능
                        .requestMatchers("/api/oauth2/**").permitAll()
                        .requestMatchers("/api/login/**").permitAll()
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


        http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
