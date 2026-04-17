// [파일] backend/module-domain/src/main/java/com/team6/domain/member/service/SignupEmailVerificationService.java
// [역할] 회원가입 이메일 인증코드 발급·Redis 저장·검증 + (선택) SMTP로 메일 발송
// [연결] MemberController → SignupEmailVerificationService
//        → Redis: IntegratedRedisConfig.memberRedisTemplate
//        → Mail: SignupJavaMailSenderConfig.signupJavaMailSender (spring.mail.host 있을 때만 빈 생성)

package com.team6.domain.member.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
public class SignupEmailVerificationService {

    private static final int TTL_SEC = 180;
    private final RedisTemplate<String, String> redisTemplate;
    @Nullable
    private final JavaMailSender mailSender;
    private final SecureRandom random = new SecureRandom();

    public SignupEmailVerificationService(
            @Qualifier("memberRedisTemplate") RedisTemplate<String, String> redisTemplate,
            @Nullable JavaMailSender mailSender
    ) {
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
    }

    private String key(String email) {
        return "signup:email:code:" + email.trim().toLowerCase();
    }

    public int sendCode(String email) {
        String normalized = email.trim().toLowerCase();
        String code = String.format("%06d", random.nextInt(1_000_000));

        redisTemplate.opsForValue().set(key(normalized), code, TTL_SEC, TimeUnit.SECONDS);

        if (mailSender != null) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(normalized);
            message.setSubject("[LocalGuest] 회원가입 인증번호");
            message.setText("인증번호는 [" + code + "] 입니다. 3분 이내에 입력해 주세요.");
            mailSender.send(message);
        }

        return TTL_SEC;
    }

    public void verify(String email, String code) {
        String normalized = email.trim().toLowerCase();
        String stored = redisTemplate.opsForValue().get(key(normalized));

        if (stored == null || !stored.equals(code.trim())) {
            throw new IllegalArgumentException("인증번호가 올바르지 않거나 만료되었습니다.");
        }

        redisTemplate.delete(key(normalized));
    }
}
