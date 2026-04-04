package com.team6.domain.matching.entity;


import com.team6.domain.matching.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private MatchRequest matchRequest;  // MATCH_REQUEST FK

    // TODO: Member 엔티티 완성 후 @ManyToOne으로 교체
    @Column(nullable = false)
    private Long payerId;               // 게스트 MEMBER FK

    @Column(nullable = false)
    private Integer amount;             // 결제 금액(원)

    @Column(nullable = false, length = 20)
    private String paymentType;         // CHAT / ACCOMPANY / EXTENSION

    @Column(nullable = false, unique = true, length = 100)
    private String pgOrderNo;           // PG 주문번호

    @Column(length = 100)
    private String pgTransactionId;     // PG 거래 ID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;       // 결제 상태

    private LocalDateTime cancelledAt;  // 취소 처리 일시

    @Column(columnDefinition = "TEXT")
    private String cancelReason;        // 취소 사유

    private LocalDateTime paidAt;       // 결제 완료일시

    private LocalDateTime refundDeadline; // 환불 마감(paidAt+2h)

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.status = PaymentStatus.PENDING;
    }

    // 결제 완료 처리 — 환불 마감 자동 계산
    public void complete(String pgTransactionId) {
        this.status = PaymentStatus.COMPLETED;
        this.pgTransactionId = pgTransactionId;
        this.paidAt = LocalDateTime.now();
        this.refundDeadline = this.paidAt.plusHours(2); // 환불 마감 = 결제 완료 + 2시간
    }

    // 취소 처리
    public void cancel(String reason) {
        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancelReason = reason;
    }

    // 환불 완료 처리
    public void refund() {
        this.status = PaymentStatus.REFUNDED;
    }
}