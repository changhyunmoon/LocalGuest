package com.team6.domain.member.dto.request;

import com.team6.domain.member.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Getter
@NoArgsConstructor
public class PasswordResetConfirmRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 아닙니다.")
    private String email;

    @NotNull(message = "가입 유형을 선택해 주세요.")
    private Role role;

    @NotBlank(message = "인증번호를 입력해 주세요.")
    @Size(min = 6, max = 6, message = "인증번호는 6자리입니다.")
    private String code;

    @NotBlank(message = "새 비밀번호를 입력해 주세요.")
    @Length(min = 8, max = 16, message = "비밀번호는 8~16자 사이여야 합니다.")
    private String newPassword;
}
