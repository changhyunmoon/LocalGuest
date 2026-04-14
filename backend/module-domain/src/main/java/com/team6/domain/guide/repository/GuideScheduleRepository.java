package com.team6.domain.guide.repository;

import com.team6.domain.guide.entity.GuideSchedule;
import com.team6.domain.guide.entity.enums.GuideScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 가이드 스케줄 레포지토리 (guide_schedules 테이블)
 */
public interface GuideScheduleRepository extends JpaRepository<GuideSchedule, Long> {

    // 가이드 전체 스케줄 조회 — 날짜 오름차순 (F06-04)
    List<GuideSchedule> findByGuideProfile_IdOrderByAvailableDateAsc(Long guideId);

    // 상태별 스케줄 조회 — 대기 스케줄 확인 (F06-04)
    List<GuideSchedule> findByGuideProfile_IdAndStatus(Long guideId, GuideScheduleStatus status);

    // 시간대 겹침 체크 — UniqueConstraint (guide_id, available_date, start_time, end_time) 기준
    // 같은 날짜에 기존 스케줄과 시간이 겹치면 true (startTime < 기존종료 AND endTime > 기존시작)
    @Query("SELECT COUNT(s) > 0 FROM GuideSchedule s " +
           "WHERE s.guideProfile.id = :guideId " +
           "AND s.availableDate = :date " +
           "AND s.startTime < :endTime " +
           "AND s.endTime > :startTime")
    boolean existsOverlappingSchedule(@Param("guideId") Long guideId,
                                      @Param("date") LocalDate date,
                                      @Param("startTime") LocalTime startTime,
                                      @Param("endTime") LocalTime endTime);
}
