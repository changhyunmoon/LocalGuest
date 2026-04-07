package com.team6.domain.matching.entity;


import com.team6.domain.matching.entity.enums.PaymentStatus;
import com.team6.module.common.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment",
        indexes = {
                @Index(name = "idx_payment_payer", columnList = "payer_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_payment_unique", columnNames = {"match_request_id", "payment_type"})
        }
)
@Check(constraints = "status IN ('PENDING','COMPLETED','REFUNDED','FAILED','CANCELLED') AND payment_type IN ('CHAT','ACCOMPANY','EXTENSION')")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)"))
})
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_request_id", nullable = false)
    private MatchRequest matchRequest;  // MATCH_REQUEST FK

    // TODO: Member 엔티티 완성 후 @ManyToOne으로 교체
    @Column(name = "payer_id", nullable = false)
    private Long payerId;               // 게스트 MEMBER FK

    @Column(nullable = false)
    private Integer amount;             // 결제 금액(원)

    @Column(name = "payment_type", nullable = false, length = 20)
    private String paymentType;         // CHAT / ACCOMPANY / EXTENSION

    @Column(name = "pg_order_no", nullable = false, unique = true, length = 100)
    private String pgOrderNo;           // PG 주문번호

    @Column(name = "pg_transaction_id", length = 100)
    private String pgTransactionId;     // PG 거래 ID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;       // 결제 상태

    @Column(name = "paid_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime paidAt;       // 결제 완료일시

    @Column(name = "refund_deadline", columnDefinition = "DATETIME(6)")
    private LocalDateTime refundDeadline; // 환불 마감(paidAt+2h)

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = PaymentStatus.PENDING;
        }
    }

    // 결제 완료 처리 — 환불 마감 자동 계산
    public void complete(String pgTransactionId) {
        this.status = PaymentStatus.COMPLETED;
        this.pgTransactionId = pgTransactionId;
        this.paidAt = LocalDateTime.now();
        this.refundDeadline = this.paidAt.plusHours(2); // 환불 마감 = 결제 완료 + 2시간
    }

    // 취소 처리
    public void cancel() {
        this.status = PaymentStatus.CANCELLED;
    }

    // 환불 완료 처리
    public void refund() {
        this.status = PaymentStatus.REFUNDED;
    }
}