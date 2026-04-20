package com.team6.domain.matching.entity;

import com.team6.domain.matching.entity.enums.TourExtensionStatus;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.module.common.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tour_extension")
@Check(constraints = "status IN ('REQUESTED','GUIDE_APPROVED','PAID','REJECTED','AUTO_CANCELLED')")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)"))
})
public class TourExtension extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "match_request_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_extension_match_req")
    )
    private MatchRequest matchRequest;  // MATCH_REQUEST FK

    // TODO: Member 엔티티 완성 후 @ManyToOne으로 교체
    @Column(name = "guest_id", nullable = false)
    private Long guestId;               // 게스트 MEMBER FK

    @Column(name = "extended_date", nullable = false)
    private LocalDate extendedDate;     // 연장 날짜

    @Column(name = "extended_price")
    private Integer extendedPrice;      // 연장 요금(원)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TourExtensionStatus status; // 연장 신청 상태

    @Column(name = "requested_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime requestedAt;  // 신청일시

    @Column(name = "guide_approved_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime guideApprovedAt; // 가이드 승인 일시

    @Column(name = "deadline_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime deadlineAt;   // 선택 마감 당일 23:59:59

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
    }

    // 게스트가 연장하지 않음을 선택
    public void rejectByGuest() {
        if (this.status != TourExtensionStatus.REQUESTED) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_INVALID_STATUS);
        }
        this.status = TourExtensionStatus.REJECTED;
    }

    // 연장 결제 완료
    public void completePayByGuestSelection() {
        if (this.status != TourExtensionStatus.REQUESTED) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_INVALID_STATUS);
        }
        this.status = TourExtensionStatus.PAID;
    }
}