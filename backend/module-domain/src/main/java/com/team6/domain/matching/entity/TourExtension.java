package com.team6.domain.matching.entity;

import com.team6.domain.matching.entity.enums.TourExtensionStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tour_extension")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class TourExtension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long extensionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private MatchRequest matchRequest;  // MATCH_REQUEST FK

    // TODO: Member 엔티티 완성 후 @ManyToOne으로 교체
    @Column(nullable = false)
    private Long guestId;               // 게스트 MEMBER FK

    @Column(nullable = false)
    private LocalDate extendedDate;     // 연장 날짜

    private Integer extendedPrice;      // 연장 요금(원)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TourExtensionStatus status; // 연장 신청 상태

    @Column(nullable = false, updatable = false)
    private LocalDateTime requestedAt;  // 신청일시

    private LocalDateTime guideApprovedAt; // 가이드 승인 일시

    @Column(nullable = false)
    private LocalDateTime deadlineAt;   // 선택 마감 당일 23:59:59

    private LocalDateTime autoCancelledAt; // 자동 미연장 처리 일시

    @PrePersist
    protected void onCreate() {
        this.requestedAt = LocalDateTime.now();
        this.status = TourExtensionStatus.REQUESTED;
    }

    // 가이드 승인
    public void approveByGuide(Integer price) {
        this.status = TourExtensionStatus.GUIDE_APPROVED;
        this.guideApprovedAt = LocalDateTime.now();
        this.extendedPrice = price;
    }

    // 자동 취소 (23:59:59 초과)
    public void autoCancel() {
        this.status = TourExtensionStatus.AUTO_CANCELLED;
        this.autoCancelledAt = LocalDateTime.now();
    }

    // 연장 결제 완료
    public void completePay() {
        this.status = TourExtensionStatus.PAID;
    }
}