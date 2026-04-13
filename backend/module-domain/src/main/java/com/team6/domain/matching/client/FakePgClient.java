package com.team6.domain.matching.client;

/**
 * PG 연동 전 로컬/테스트용 Stub 클라이언트.
 * 실제 PG 도입 시 동일 시그니처를 구현하는 어댑터로 교체한다.
 */
public interface FakePgClient {

    /** Stub 결제 생성 시 사용하는 주문번호 접두어 (실 PG 도입 시 제거) */
    String STUB_PG_ORDER_PREFIX = "LM-FAKE-";

    /**
     * Stub 승인: 주문번호·금액을 검증하고 합성 거래 ID를 반환한다.
     *
     * @param pgOrderNo   결제 생성 시 발급한 PG 주문번호
     * @param expectedAmount 결제 엔티티에 저장된 금액(원)
     * @param paymentId  결제 PK (합성 거래 ID 생성에 사용)
     * @return PG 거래 ID (DB {@code payment.pg_transaction_id} 저장)
     */
    String approvePayment(String pgOrderNo, int expectedAmount, Long paymentId);
}
