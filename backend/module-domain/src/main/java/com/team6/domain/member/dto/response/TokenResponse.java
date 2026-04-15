package com.team6.domain.member.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private String grantType;

    //[LOG] DEBUG : [Auth-Domain] TokenResponse 생성 완료
}
