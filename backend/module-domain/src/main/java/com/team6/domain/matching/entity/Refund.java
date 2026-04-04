package com.team6.domain.matching.entity;

import com.team6.domain.matching.entity.enums.RefundStatus;
import com.team6.domain.matching.entity.enums.RefundType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "refund")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long refundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;            // PAYMENT FK

    // TODO: Member 엔티티 완성 후 @ManyToOne으로 교체
    @Column(nullable = false)
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

    private LocalDateTime processedAt;  // 처리 완료일

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.status = RefundStatus.PENDING;
        this.aiProcessed = false;
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