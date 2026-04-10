package com.team6.domain.member.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.swing.*;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "member")
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.GUEST;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(name = "social_type", length = 50)
    private SocialType socialType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)")
    private LocalDateTime createdAt;

    @CreationTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)")
    private LocalDateTime updatedAt;

    // 비밀번호 값 교체
    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    // 회원 권한 변경
    public void upgradeToGuide() {
        this.role = Role.GUIDE;
    }

    // 로그인 시 비밀번호 검증
    public void validatePassword(PasswordEncoder passwordEncoder, String rawPassword){
        if(!passwordEncoder.matches(rawPassword, this.password)) {
            // [LOG] Warn : 로그인 실패 - 비밀번호 불일치
            System.out.println("로그인 실패 : 비밀번호 불일치");
            throw new IllegalArgumentException("이메일 또는 비밀번호를 잘못 입력하였습니다. ");
        }
    }

    // 회원 탈퇴
    public void withdraw() {
        this.status = Status.WITHDRAWN;
    }

}
