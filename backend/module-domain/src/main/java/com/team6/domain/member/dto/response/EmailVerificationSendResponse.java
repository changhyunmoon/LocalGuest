// EmailVerificationSendResponse.java
package com.team6.domain.member.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EmailVerificationSendResponse {
    private final int expiresInSeconds;
}