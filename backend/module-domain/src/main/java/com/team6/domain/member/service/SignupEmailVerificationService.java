package com.team6.domain.member.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
    // 개발용 만능 코드
    private static final String DEV_BYPASS_CODE = "000000";
    
    private final RedisTemplate<String, String> redisTemplate;
    private final JavaMailSender mailSender;
    private final SecureRandom random = new SecureRandom();
    private final boolean isLocalProfile;

    public SignupEmailVerificationService(
            @Qualifier("memberRedisTemplate") RedisTemplate<String, String> redisTemplate,
            @Nullable JavaMailSender mailSender,
            // 개발용 만능 코드 허용을 위한 생성자 파라미터 추가
            @Value("${spring.profiles.active:prod}") String activeProfile
    ) {
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
        // 개발용 만능 코드 허용을 위한 프로파일 확인
        this.isLocalProfile = "local".equals(activeProfile);
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
        } else if (isLocalProfile) {
            System.out.println("========================================");
            System.out.println("📧 [개발용] 이메일 인증번호");
            System.out.println("이메일: " + normalized);
            System.out.println("인증번호: " + code);
            System.out.println("또는 만능 코드: " + DEV_BYPASS_CODE);
            System.out.println("========================================");
        }

        return TTL_SEC;
    }

    public void verify(String email, String code) {
        String normalized = email.trim().toLowerCase();
        String trimmedCode = code.trim();

        // 개발용 만능 코드 허용을 위한 확인
        if (isLocalProfile && DEV_BYPASS_CODE.equals(trimmedCode)) {
            return;
        }
        
        String stored = redisTemplate.opsForValue().get(key(normalized));

        if (stored == null || !stored.equals(trimmedCode)) {
            throw new IllegalArgumentException("인증번호가 올바르지 않거나 만료되었습니다.");
        }

        redisTemplate.delete(key(normalized));
    }
}