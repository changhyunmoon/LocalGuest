package com.team6.domain.member.dto.request;

import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Role;
import com.team6.domain.member.entity.Status;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;


@Getter
@NoArgsConstructor
public class MemberJoinRequest {
    @NotBlank(message = "이메일은 필수입니다. ")
    @Email(message = "이메일 형식이 아닙니다.")
    private String email;
    @NotBlank(message = "비밀번호는 필수입니다. ")
    @Length(min = 8, max = 16, message = "비밀번호는 8~16자 사이여야 합니다. ")
    private String password;

    @NotBlank(message = "이름은 필수입니다. ")
    private String name;


    private String nickname;


    public Member toEntity() {
        // [LOG] DBUG : [Member-Domain] DTO를 Entity로 변환 시작

        //닉네임 자동 생성 로직
        String finalNickname = (nickname == null || nickname.isBlank())
                ? createDefaultNickname(this.email)
                : this.nickname;

        return Member.builder()
                .email(this.email)
                .password(this.password)
                .name(this.name)
                .nickname(finalNickname)
                .role(Role.GUEST)   // 기본 게스트로 역할 지정
                .status(Status.ACTIVE)
                .build();
    }

    private String createDefaultNickname(String email) {
        // [LOG] INFO : [Member-Domain] 닉네임 자동 생성 수행
        //  이메일의 @ 앞부분 추출 + 랜덤 숫자 4자리
        String prefix = email.split("@")[0];
        int randomNum = (int)(Math.random() * 9000) + 1000;
        return prefix + randomNum;
    }
}
