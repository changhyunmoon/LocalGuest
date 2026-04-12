package com.team6.domain.matching.client;

import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 외부 PG 없이 승인 결과만 합성한다. 주문번호는 {@code LM-FAKE-} 접두로 생성된 것만 허용한다.
 */
@Slf4j
@Component
public class StubFakePgClient implements FakePgClient {

    @Override
    public String approvePayment(String pgOrderNo, int expectedAmount, Long paymentId) {
        if (pgOrderNo == null || !pgOrderNo.startsWith(STUB_PG_ORDER_PREFIX)) {
            throw new MatchingException(MatchingErrorCode.PAYMENT_PG_VERIFICATION_FAILED);
        }
        if (expectedAmount <= 0 || paymentId == null) {
            throw new MatchingException(MatchingErrorCode.INVALID_REQUEST);
        }
        String txnId = "FAKE-TXN-" + paymentId;
        log.info("[FakePG] 승인 Stub — pgOrderNo={}, amount={}, pgTransactionId={}", pgOrderNo, expectedAmount, txnId);
        return txnId;
    }
}
