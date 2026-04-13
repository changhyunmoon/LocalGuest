package com.team6.domain.matching.entity;

import com.team6.domain.matching.entity.enums.MatchRequestStatus;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import com.team6.module.common.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.Check;

import java.time.LocalDate;

@Entity
@Table(
        name = "match_request",
        indexes = {
                @Index(name = "idx_match_guest", columnList = "guest_id"),
                @Index(name = "idx_match_guide", columnList = "guide_id")
        }
)
@Check(constraints = "status IN ('PENDING','ACCEPTED','PAID','IN_PROGRESS','COMPLETED','CANCELLED','REJECTED')")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Slf4j
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)"))
})
public class MatchRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TODO: Member 엔티티 완성 후 @ManyToOne으로 교체
    @Column(name = "guest_id", nullable = false)
    private Long guestId;

    // TODO: GuideProfile 엔티티 완성 후 @ManyToOne으로 교체
    @Column(name = "guide_id", nullable = false)
    private Long guideId;

    @Column(nullable = false)
    private String destination;         // 여행 목적지

    @Column(columnDefinition = "TEXT")
    private String concept;             // 여행 컨셉 (AI 분석 원본)

    @Column(name = "concept_summary", columnDefinition = "TEXT")
    private String conceptSummary;      // AI가 정리한 여행 컨셉 요약 (가이드/게스트 확인용)

    @Column(name = "desired_date", nullable = false)
    private LocalDate desiredDate;      // 희망 여행 날짜

    @Column(name = "desired_budget")
    private Integer desiredBudget;      // 희망 예산(원)

    @Column(name = "proposed_schedule", columnDefinition = "TEXT")
    private String proposedSchedule;    // 가이드 제시 일정(간략)

    @Column(name = "propose_message", columnDefinition = "TEXT")
    private String proposeMessage;      // 가이드 제시 멘트(선택)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MatchRequestStatus status;  // 매칭 상태

    @Column(name = "cancelled_by", length = 20)
    private String cancelledBy;         // GUEST or GUIDE

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;        // 취소 사유
    /**
     * 매칭 요청 생성용 정적 팩토리 메서드
     *
     * api-server에서 매칭 요청 생성 API를 호출할 때
     * builder를 직접 노출하지 않고 이 메서드를 통해 엔티티를 생성하도록 추가함.
     *
     * 생성 시점의 상태(status)는 @PrePersist에서 PENDING으로 자동 세팅되므로
     * 여기서는 필수 요청 데이터만 채운다.
     */
    public static MatchRequest create(
            Long guestId,
            Long guideId,
            String destination,
            String concept,
            String conceptSummary,
            LocalDate desiredDate,
            Integer desiredBudget
    ) {
        return MatchRequest.builder()
                .guestId(guestId)
                .guideId(guideId)
                .destination(destination)
                .concept(concept)
                .conceptSummary(conceptSummary)
                .desiredDate(desiredDate)
                .desiredBudget(desiredBudget)
                .build();
    }

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = MatchRequestStatus.PENDING;
        }
    }
    /**
     * 가이드가 요청을 거절할 때 사용하는 상태 변경 메서드
     *
     * 현재 매칭 요청이 PENDING 상태일 때만 REJECTED로 변경 가능하다.
     * 이미 제안이 진행되었거나 종료된 요청이라면 예외를 발생시킨다.
     */
    public void reject() {
        if (this.status != MatchRequestStatus.PENDING) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_INVALID_STATUS);
        }
        this.status = MatchRequestStatus.REJECTED;
    }

    /**
     * 가이드가 제안을 수락할 때 사용하는 상태 변경 메서드
     * SQL 스키마에 PROPOSED 상태가 없으므로 ACCEPTED로 전이한다.
     */
    public void propose() {
        if (this.status != MatchRequestStatus.PENDING) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_INVALID_STATUS);
        }
        this.status = MatchRequestStatus.ACCEPTED;
    }

    public void applyProposal(String proposedSchedule, String proposeMessage) {
        if (this.status != MatchRequestStatus.PENDING) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_INVALID_STATUS);
        }
        this.proposedSchedule = proposedSchedule;
        this.proposeMessage = proposeMessage;
        this.status = MatchRequestStatus.ACCEPTED;
    }

    /**
     * 게스트가 제안을 최종 확인할 때 사용하는 메서드
     * 현재 상태 모델에서는 ACCEPTED 상태를 유지한다.
     */
    public void accept() {
        if (this.status != MatchRequestStatus.ACCEPTED) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_INVALID_STATUS);
        }
        this.status = MatchRequestStatus.ACCEPTED;
    }

    /**
     * 결제 완료 시 매칭 요청을 PAID로 전이한다. 이미 PAID 이후이면 변경하지 않는다(연장 결제 등).
     */
    public void markAsPaidIfAccepted() {
        if (this.status == MatchRequestStatus.ACCEPTED) {
            this.status = MatchRequestStatus.PAID;
            log.info("[Payment] 매칭 요청 결제 반영 — status=PAID, requestId={}", this.id);
        }
    }

    // 게스트 취소 처리 (F05-01)
    public void cancelByGuest(String reason) {
        if (this.status != MatchRequestStatus.ACCEPTED &&
                this.status != MatchRequestStatus.PAID) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_CANNOT_CANCEL);
        }
        this.status = MatchRequestStatus.CANCELLED;
        this.cancelledBy = "GUEST";
        this.cancelReason = reason;
        log.info("[F05-01] 게스트 취소 처리 — status=CANCELLED, cancelledBy=GUEST");
    }

    // 가이드 취소 처리 (F05-02)
    public void cancelByGuide(String reason) {
        if (this.status != MatchRequestStatus.ACCEPTED &&
                this.status != MatchRequestStatus.PAID) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_CANNOT_CANCEL);
        }
        this.status = MatchRequestStatus.CANCELLED;
        this.cancelledBy = "GUIDE";
        this.cancelReason = reason;
    }
}