package com.team6.domain.matching.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Stub PG 승인 단계: 클라이언트가 발급받은 {@code pgOrderNo}와 금액으로 검증 후 COMPLETED 전이.
 */
@Getter
@NoArgsConstructor
public class PaymentConfirmRequest {

    @NotBlank
    private String pgOrderNo;

    @NotNull
    @Positive
    private Integer amount;

    /**
     * 카카오페이 승인용 pg_token.
     * Stub/Fake 결제에서는 비워도 되며, 카카오 결제 모드에서만 사용한다.
     */
    private String pgToken;
}
