package com.team6.domain.auth.config;

import com.team6.domain.auth.filter.JwtAuthenticationFilter;
import com.team6.domain.member.service.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.util.SessionConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                        .requestMatchers("/auth/**").permitAll() // 로그인 관련은 누가나 접근 가능
                        .requestMatchers("/members/join").permitAll()
                        .anyRequest().authenticated() //그 외는 모두 로그인 필요
                );

        http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
