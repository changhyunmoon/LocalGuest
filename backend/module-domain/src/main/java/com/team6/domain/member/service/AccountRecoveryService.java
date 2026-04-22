package com.team6.domain.member.service;

import com.team6.domain.member.dto.response.FindIdResponse;
import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Role;
import com.team6.domain.member.entity.Status;
import com.team6.domain.member.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
public class AccountRecoveryService {

    private static final int TTL_SEC = 180;
    private static final String DEV_BYPASS_CODE = "000000";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;
    @Nullable
    private final JavaMailSender mailSender;
    private final SecureRandom random = new SecureRandom();

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    public AccountRecoveryService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            @Qualifier("memberRedisTemplate") RedisTemplate<String, String> redisTemplate,
            @Nullable JavaMailSender mailSender
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
    }

    @Transactional(readOnly = true)
    public FindIdResponse findMaskedEmail(String name, String nickname, Role role) {
        String n = name.trim();
        String nick = nickname.trim();
        Member member = memberRepository
                .findByNameAndNicknameAndRoleAndStatus(n, nick, role, Status.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("입력하신 정보와 일치하는 계정을 찾을 수 없습니다."));
        return new FindIdResponse(maskEmail(member.getEmail()));
    }

    public int sendPasswordResetCode(String email, Role role) {
        String normalized = email.trim().toLowerCase();
        Member member = memberRepository.findByEmailAndRole(normalized, role)
                .orElseThrow(() -> new IllegalArgumentException("등록된 계정을 찾을 수 없습니다."));
        if (member.getStatus() != Status.ACTIVE) {
            throw new IllegalArgumentException("사용할 수 없는 계정입니다.");
        }
        if (member.getPassword() == null || member.getPassword().isBlank()) {
            throw new IllegalArgumentException("소셜 로그인으로 가입된 계정입니다. 구글 로그인을 이용해 주세요.");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        redisTemplate.opsForValue().set(pwdResetKey(normalized, role), code, TTL_SEC, TimeUnit.SECONDS);

        if (mailSender != null) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(normalized);
            message.setSubject("[LocalGuest] 비밀번호 재설정 인증번호");
            message.setText("인증번호는 [" + code + "] 입니다. 3분 이내에 입력해 주세요.");
            mailSender.send(message);
        } else if (isLocalProfile()) {
            System.out.println("========================================");
            System.out.println("📧 [개발용] 비밀번호 재설정 인증번호");
            System.out.println("이메일: " + normalized + " / 역할: " + role);
            System.out.println("인증번호: " + code);
            System.out.println("또는 만능 코드: " + DEV_BYPASS_CODE);
            System.out.println("========================================");
        }

        return TTL_SEC;
    }

    @Transactional
    public void resetPasswordAfterVerification(String email, Role role, String code, String newPassword) {
        String normalized = email.trim().toLowerCase();
        verifyPwdResetCode(normalized, role, code.trim());

        Member member = memberRepository.findByEmailAndRole(normalized, role)
                .orElseThrow(() -> new IllegalArgumentException("등록된 계정을 찾을 수 없습니다."));
        if (member.getStatus() != Status.ACTIVE) {
            throw new IllegalArgumentException("사용할 수 없는 계정입니다.");
        }
        if (member.getPassword() == null || member.getPassword().isBlank()) {
            throw new IllegalArgumentException("소셜 로그인으로 가입된 계정입니다. 구글 로그인을 이용해 주세요.");
        }

        member.updatePassword(passwordEncoder.encode(newPassword));
    }

    private void verifyPwdResetCode(String normalizedEmail, Role role, String trimmedCode) {
        if (isLocalProfile() && DEV_BYPASS_CODE.equals(trimmedCode)) {
            return;
        }
        String key = pwdResetKey(normalizedEmail, role);
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null || !stored.equals(trimmedCode)) {
            throw new IllegalArgumentException("인증번호가 올바르지 않거나 만료되었습니다.");
        }
        redisTemplate.delete(key);
    }

    private String pwdResetKey(String normalizedEmail, Role role) {
        return "pwdreset:email:code:" + normalizedEmail + ":" + role.name();
    }

    private boolean isLocalProfile() {
        return "local".equals(activeProfile);
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        String e = email.trim();
        int at = e.indexOf('@');
        if (at < 1 || at == e.length() - 1) {
            return "***";
        }
        String local = e.substring(0, at);
        String domain = e.substring(at + 1);
        int show = Math.min(2, local.length());
        return local.substring(0, show) + "***@" + domain;
    }
}
