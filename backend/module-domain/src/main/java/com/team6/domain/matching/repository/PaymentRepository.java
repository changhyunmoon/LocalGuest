package com.team6.domain.matching.repository;

import com.team6.domain.matching.entity.Payment;
import com.team6.domain.matching.entity.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 매칭 요청 ID로 결제 목록 조회
    List<Payment> findByMatchRequest_Id(Long requestId);

    // 게스트 ID로 결제 내역 조회
    List<Payment> findByPayerId(Long payerId);

    // PG 주문번호로 단건 조회 (결제 검증용)
    Optional<Payment> findByPgOrderNo(String pgOrderNo);

    // 게스트 ID + 상태로 조회
    List<Payment> findByPayerIdAndStatus(Long payerId, PaymentStatus status);
}