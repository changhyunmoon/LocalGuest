package com.team6.domain.member.dto.request;

import com.team6.domain.member.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MemberJoinRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 아닙니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Length(min = 8, max = 16, message = "비밀번호는 8~16자 사이여야 합니다.")
    private String password;

    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    @NotBlank(message = "닉네임은 필수입니다.")
    private String nickname;

    private Role role;  // 기본값은 Service에서 GUEST로 처리

    // ✅ Guest 프로필 정보 추가
    private GuestProfileRequest guestProfile;

    // ✅ toEntity() 메서드 제거 - Service에서 직접 생성
}