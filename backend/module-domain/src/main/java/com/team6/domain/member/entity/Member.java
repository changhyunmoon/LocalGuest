package com.team6.domain.member.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "member")  // ✅ uniqueConstraints 제거
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ email UNIQUE 추가
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String name;

    // ✅ nickname unique 제거
    @Column(nullable = false, length = 100)
    private String nickname;

    // ✅ role → roles (Set으로 변경)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "member_roles", joinColumns = @JoinColumn(name = "member_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_type", length = 50)
    private SocialType socialType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreationTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ✅ Guest 프로필 추가
    @OneToOne(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private GuestProfile guestProfile;

    // 가이드 프로필: GuideProfile.member_id(Long) — 엔티티 연관은 GuideProfile → Member 방향만 사용

    // ========== 메서드 ==========

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    // ✅ 역할 관리 메서드
    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void removeRole(Role role) {
        this.roles.remove(role);
    }

    public boolean hasRole(Role role) {
        return this.roles.contains(role);
    }

    public void upgradeToGuide() {
        this.roles.add(Role.GUIDE);
    }

    public void validatePassword(PasswordEncoder passwordEncoder, String rawPassword){
        if(!passwordEncoder.matches(rawPassword, this.password)) {
            throw new IllegalArgumentException("이메일 또는 비밀번호를 잘못 입력하였습니다.");
        }
    }

    // ✅ 탈퇴 시 닉네임 변경
    public void withdraw() {
        if(this.status == Status.WITHDRAWN) {
            throw new IllegalStateException("이미 탈퇴 처리된 회원입니다.");
        }

        this.nickname = this.nickname + "_deleted_" + System.currentTimeMillis();
        this.status = Status.WITHDRAWN;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateNickname(String newNickname) {
        this.nickname = newNickname;
        this.updatedAt = LocalDateTime.now();
    }

    public void reactivate(String encodedNewPassword, String newName, String newNickName) {
        this.status = Status.ACTIVE;
        this.password = encodedNewPassword;
        this.name = newName;
        this.nickname = newNickName;
        this.updatedAt = LocalDateTime.now();
    }

    // ✅ 프로필 설정
    public void setGuestProfile(GuestProfile profile) {
        this.guestProfile = profile;
        if (profile != null) {
            profile.setMember(this);
        }
    }
}