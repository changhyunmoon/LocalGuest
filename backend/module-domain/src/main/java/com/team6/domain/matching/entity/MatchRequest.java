package com.team6.domain.matching.entity;

import com.team6.domain.matching.entity.enums.MatchRequestStatus;
import com.team6.domain.matching.exception.MatchingErrorCode;
import com.team6.domain.matching.exception.MatchingException;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "match_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Slf4j
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
            LocalDate desiredDate,
            Integer desiredBudget
    ) {
        return MatchRequest.builder()
                .guestId(guestId)
                .guideId(guideId)
                .destination(destination)
                .concept(concept)
                .desiredDate(desiredDate)
                .desiredBudget(desiredBudget)
                .build();
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        // 최초 생성 시 status가 별도로 지정되지 않았다면 기본값을 PENDING으로 설정
        if (this.status == null) {
            this.status = MatchRequestStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
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
     * 가이드가 요청을 수락하고 제안 단계로 넘길 때 사용하는 상태 변경 메서드
     *
     * 현재 도메인 상태 설계상 "가이드 수락"을 별도 상태로 두지 않고
     * 가이드가 실제 제안을 시작하는 시점을 PROPOSED로 본다.
     *
     * 따라서 PENDING 상태에서만 PROPOSED로 변경 가능하다.
     */
    public void propose() {
        if (this.status != MatchRequestStatus.PENDING) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_INVALID_STATUS);
        }
        this.status = MatchRequestStatus.PROPOSED;
    }

    /**
     * 게스트가 가이드의 제안을 최종 수락할 때 사용하는 상태 변경 메서드
     *
     * PROPOSED 상태에서만 ACCEPTED로 변경 가능하다.
     */
    public void accept() {
        if (this.status != MatchRequestStatus.PROPOSED) {
            throw new MatchingException(MatchingErrorCode.MATCH_REQUEST_INVALID_STATUS);
        }
        this.status = MatchRequestStatus.ACCEPTED;
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