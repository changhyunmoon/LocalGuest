package com.team6.domain.matching.repository;

import com.team6.domain.matching.entity.Refund;
import com.team6.domain.matching.entity.enums.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    // 결제 ID로 환불 조회 (중복 신청 방지용)
    Optional<Refund> findByPayment_Id(Long paymentId);

    // 신청자 ID로 환불 내역 조회
    List<Refund> findByRequesterId(Long requesterId);

    // 상태별 환불 목록 조회 (관리자용)
    List<Refund> findByStatus(RefundStatus status);
}