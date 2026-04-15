package com.team6.domain.guide.entity;

import com.team6.domain.guide.entity.enums.GuideScheduleStatus;
import com.team6.module.common.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 가이드 스케줄 엔티티 (guide_schedules 테이블)
 */
@Entity
@Table(
        name = "guide_schedules",
        // UNIQUE 제약: 동일 가이드가 같은 날짜·시간대에 중복 스케줄을 등록하지 못하도록 방지
        uniqueConstraints = @UniqueConstraint(
                name = "uq_schedule_time",
                columnNames = {"guide_id", "available_date", "start_time", "end_time"}
        )
        // CHECK 제약 (start_time < end_time): JPA 표준 어노테이션으로 직접 지원되지 않음
        // DDL 적용 방법: Flyway/Liquibase 마이그레이션 또는 DBA가 직접 아래 DDL 실행
        // ALTER TABLE guide_schedules ADD CONSTRAINT chk_time_order CHECK (start_time < end_time);
        // Service 계층에서도 동일 조건을 검증하여 이중 보호
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class GuideSchedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id; // 스케줄 고유 ID (PK)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guide_id", nullable = false)
    private GuideProfile guideProfile; // 가이드 프로필 (guide_profiles FK)

    @Column(name = "available_date", nullable = false)
    private LocalDate availableDate; // 가능 날짜

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime; // 시작 시간

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime; // 종료 시간

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GuideScheduleStatus status = GuideScheduleStatus.AVAILABLE; // 스케줄 상태 (기본값: AVAILABLE)

    @Column(name = "match_request_id")
    private Long matchRequestId; // 매칭 요청 ID (matching 도메인 크로스 참조 — ID만 보관)

    @Column(name = "meeting_point", length = 200)
    private String meetingPoint; // 만남 장소 (수락 후 가이드 작성)

    @Column(name = "guide_message", columnDefinition = "TEXT")
    private String guideMessage; // 가이드 안내 메시지 (수락 후 가이드 작성)

    @Column(name = "course_detail", columnDefinition = "TEXT")
    private String courseDetail; // 코스 상세 정보 — 결제 전 잠금, 결제 후 공개

    @Column(name = "is_paid", nullable = false)
    @Builder.Default
    private Boolean isPaid = false; // 결제 완료 여부 — true 시 courseDetail 잠금 해제

    // 스케줄 날짜·시간·상태 수정
    public void update(LocalDate availableDate, LocalTime startTime, LocalTime endTime, GuideScheduleStatus status) {
        this.availableDate = availableDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    // 스케줄 상태만 변경 (AVAILABLE ↔ BLOCKED, 매칭 연동 시 BOOKED)
    public void changeStatus(GuideScheduleStatus status) {
        this.status = status;
    }

    // 수락 후 양식 저장 (meetingPoint, guideMessage, courseDetail)
    public void submitForm(String meetingPoint, String guideMessage, String courseDetail) {
        this.meetingPoint = meetingPoint;
        this.guideMessage = guideMessage;
        this.courseDetail = courseDetail;
    }

    // matchRequestId 연결 — matching 도메인이 PENDING 전환 시 호출
    public void linkMatchRequest(Long matchRequestId) {
        this.matchRequestId = matchRequestId;
    }

    /**
     * 결제 완료 처리 — 가이드 수락 등으로 이미 BOOKED인 경우 isPaid만 true로 설정해 courseDetail 잠금을 해제한다.
     */
    public void markAsPaid() {
        this.isPaid = true;
    }

    /**
     * 매칭 취소·거절 시 스케줄을 다시 예약 가능하게 복구한다. PENDING·BOOKED 모두 허용.
     */
    public void releaseMatchToAvailable() {
        this.status = GuideScheduleStatus.AVAILABLE;
        this.matchRequestId = null;
        this.isPaid = false;
    }
}
