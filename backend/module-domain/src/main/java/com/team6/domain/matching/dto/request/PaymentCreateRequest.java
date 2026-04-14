package com.team6.domain.matching.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentCreateRequest {

    @NotNull
    private Long matchRequestId;

    @NotNull
    @Positive
    private Integer amount;

    /** DB CHECK 및 {@code payment_type} 유니크 키와 동일: CHAT, ACCOMPANY, EXTENSION */
    @NotBlank
    @Pattern(regexp = "CHAT|ACCOMPANY|EXTENSION")
    private String paymentType;
}
