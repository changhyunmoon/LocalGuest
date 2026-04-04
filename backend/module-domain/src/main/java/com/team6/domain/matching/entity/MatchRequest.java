package com.team6.domain.matching.entity;

import com.team6.domain.matching.entity.enums.MatchRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "match_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class MatchRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requestId;

    // TODO: Member 엔티티 완성 후 @ManyToOne으로 교체
    @Column(nullable = false)
    private Long guestId;

    // TODO: GuideProfile 엔티티 완성 후 @ManyToOne으로 교체
    @Column(nullable = false)
    private Long guideId;

    @Column(nullable = false)
    private String destination;         // 여행 목적지

    @Column(columnDefinition = "TEXT")
    private String concept;             // 여행 컨셉 (AI 분석 원본)

    private LocalDate desiredDate;      // 희망 여행 날짜

    private Integer desiredBudget;      // 희망 예산(원)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MatchRequestStatus status;  // 매칭 상태

    @Column(length = 10)
    private String cancelledBy;         // GUEST or GUIDE

    @Column(columnDefinition = "TEXT")
    private String cancelReason;        // 취소 사유

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = MatchRequestStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 게스트 취소 처리
    public void cancelByGuest(String reason) {
        this.status = MatchRequestStatus.CANCELLED;
        this.cancelledBy = "GUEST";
        this.cancelReason = reason;
    }

    // 가이드 취소 처리
    public void cancelByGuide(String reason) {
        this.status = MatchRequestStatus.CANCELLED;
        this.cancelledBy = "GUIDE";
        this.cancelReason = reason;
    }
}