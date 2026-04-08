package com.team6.domain.guide.repository;

import com.team6.domain.guide.entity.GuideSchedule;
import com.team6.domain.guide.entity.enums.GuideScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 가이드 스케줄 레포지토리 (guide_schedules 테이블)
 */
public interface GuideScheduleRepository extends JpaRepository<GuideSchedule, Long> {

    // 가이드 전체 스케줄 조회 — 날짜 오름차순 (F06-04)
    List<GuideSchedule> findByGuideProfile_IdOrderByAvailableDateAsc(Long guideId);

    // 상태별 스케줄 조회 — 대기 스케줄 확인 (F06-04)
    List<GuideSchedule> findByGuideProfile_IdAndStatus(Long guideId, GuideScheduleStatus status);

    // 특정 날짜 스케줄 조회 — 중복 등록 방지 (F06-04)
    Optional<GuideSchedule> findByGuideProfile_IdAndAvailableDate(Long guideId, LocalDate availableDate);
}
