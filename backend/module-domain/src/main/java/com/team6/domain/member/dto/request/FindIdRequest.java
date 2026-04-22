package com.team6.domain.member.dto.request;

import com.team6.domain.member.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FindIdRequest {

    @NotBlank(message = "이름을 입력해 주세요.")
    private String name;

    @NotBlank(message = "닉네임을 입력해 주세요.")
    private String nickname;

    @NotNull(message = "가입 유형을 선택해 주세요.")
    private Role role;
}
