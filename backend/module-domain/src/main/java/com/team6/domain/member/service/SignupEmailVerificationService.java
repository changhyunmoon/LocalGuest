// SignupEmailVerificationService.java (패키지는 팀 규칙에 맞게)
package com.team6.domain.member.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SignupEmailVerificationService {

    @Qualifier("memberRedisTemplate")
    private final RedisTemplate<String, String> redisTemplate;

    private static final int TTL_SEC = 180;
    private final SecureRandom random = new SecureRandom();

    private String key(String email) {
        return "signup:email:code:" + email.trim().toLowerCase();
    }

    /** 개발: 로그만. 운영: JavaMail·SendGrid 등으로 메일 발송 */
    public int sendCode(String email) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        redisTemplate.opsForValue().set(key(email), code, TTL_SEC, TimeUnit.SECONDS);
        // TODO: 실제 메일 발송. 개발 중에는 로그로 확인.
        // log.info("[signup-email] to={} code={}", email, code);
        return TTL_SEC;
    }

    public void verify(String email, String code) {
        String stored = redisTemplate.opsForValue().get(key(email));
        if (stored == null || !stored.equals(code.trim())) {
            throw new IllegalArgumentException("인증번호가 올바르지 않거나 만료되었습니다.");
        }
        redisTemplate.delete(key(email));
    }
}