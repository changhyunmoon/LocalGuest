package com.team6.domain.matching.entity;

import com.team6.domain.matching.entity.enums.RefundStatus;
import com.team6.domain.matching.entity.enums.RefundType;
import com.team6.module.common.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;
import java.time.LocalDateTime;

@Entity
@Table(name = "refund")
@Check(constraints = "status IN ('PENDING','APPROVED','REJECTED') AND refund_type IN ('AUTO','MANUAL','CANCEL_GUEST','CANCEL_GUIDE')")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)"))
})
public class Refund extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "payment_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_refund_payment")
    )
    private Payment payment;            // PAYMENT FK

    // TODO: Member 엔티티 완성 후 @ManyToOne으로 교체
    @Column(name = "requester_id", nullable = false)
    private Long requesterId;           // 게스트 MEMBER FK

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundType refundType;      // 환불 발생 원인

    @Column(columnDefinition = "TEXT")
    private String reason;              // 환불 사유

    @Column(length = 500)
    private String evidenceUrl;         // 증빙 자료 URL

    @Column(nullable = false)
    private Boolean aiProcessed;        // AI 처리 반영 여부

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;        // 환불 처리 상태

    @Column(name = "processed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime processedAt;  // 처리 완료일

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = RefundStatus.PENDING;
        }
        if (this.aiProcessed == null) {
            this.aiProcessed = false;
        }
        if (this.refundType == null) {
            this.refundType = RefundType.MANUAL;
        }
    }

    // 환불 승인
    public void approve() {
        this.status = RefundStatus.APPROVED;
        this.processedAt = LocalDateTime.now();
    }

    // 환불 거절
    public void reject() {
        this.status = RefundStatus.REJECTED;
        this.processedAt = LocalDateTime.now();
    }
}