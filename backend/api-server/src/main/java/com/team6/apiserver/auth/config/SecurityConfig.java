package com.team6.apiserver.auth.config;

import com.team6.apiserver.auth.filter.JwtAuthenticationFilter;
import com.team6.apiserver.auth.oauth.CustomOauth2UserService;
import com.team6.apiserver.auth.oauth.OAuth2SuccessHandler;
import com.team6.domain.auth.provider.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomOauth2UserService customOauth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final RedisTemplate<String, String> redisTemplate;

    public SecurityConfig(
            JwtTokenProvider jwtTokenProvider,
            CustomOauth2UserService customOauth2UserService,
            OAuth2SuccessHandler oAuth2SuccessHandler,
            @Qualifier("memberRedisTemplate") RedisTemplate<String, String> redisTemplate
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.customOauth2UserService = customOauth2UserService;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.redisTemplate = redisTemplate;
    }

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
                        .requestMatchers(HttpMethod.POST, "/members/email-verification/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/members/find-id").permitAll()
                        .requestMatchers(HttpMethod.POST, "/members/password-reset/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/members/nickname-availability").permitAll()
                        // 인증 인가 경로는 비로그인 유저도 접근 가능
                        .requestMatchers("/oauth2/**").permitAll()
                        .requestMatchers("/login/**").permitAll()
                        // 가이드 조회 API만 공개, 변경 API는 인증 필요
                        .requestMatchers(HttpMethod.GET, "/guides/**").permitAll()
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

                        // 2. 리뷰 조회는 비로그인 유저도 가능 (단, 본인 목록은 인증 필요 — /reviews/** 보다 먼저 둔다)
                        .requestMatchers(HttpMethod.GET, "/reviews/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/reviews/**").permitAll()
                        // 리뷰 등록, 수정, 삭제는 인증 필요
                        .requestMatchers(HttpMethod.POST, "/reviews/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/reviews/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/reviews/**").authenticated()

                        // 2.5 카카오페이 리다이렉트 콜백 (브라우저 리다이렉트로 Authorization 헤더가 없음)
                        // context-path가 /api 인 환경도 있어 둘 다 허용한다.
                        .requestMatchers("/matching/payments/kakao/**", "/api/matching/payments/kakao/**").permitAll()

                        // 3. 그 외 (리뷰 등록, 채팅, 마이페이지 등)는 무조건 로그인 필요
                        .anyRequest().authenticated()
                )

                // 인증 실패 시 리다이렉트 방지
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )

                // 일반 로그인 설정(로그인 폼 경로 지정)
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
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

        configuration.addAllowedOrigin("http://localhost:5173");          // 로컬 테스트용
        configuration.addAllowedOrigin("https://bam-match.com");        // 실제 프론트 도메인
        configuration.addAllowedOrigin("https://www.bam-match.com");    // www 포함 도메인

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
