// EmailVerificationSendRequest.java
package com.team6.domain.member.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EmailVerificationSendRequest {
    @NotBlank @Email
    private String email;
}