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
}
