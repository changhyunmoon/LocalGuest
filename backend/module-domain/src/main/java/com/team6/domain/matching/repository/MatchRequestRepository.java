package com.team6.domain.matching.repository;

import com.team6.domain.matching.entity.MatchRequest;
import com.team6.domain.matching.dto.response.MatchRequestListProjection;
import com.team6.domain.matching.entity.enums.MatchRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {

    // 게스트 ID로 전체 매칭 요청 조회
    List<MatchRequest> findByGuestId(Long guestId);

    // 게스트 ID로 페이징 조회
    Page<MatchRequest> findByGuestId(Long guestId, Pageable pageable);

    // 게스트 ID로 Slice 기반 페이징 조회 (COUNT 쿼리 회피용)
    Slice<MatchRequest> findSliceByGuestId(Long guestId, Pageable pageable);

    // 게스트 ID로 DTO projection + Slice 조회 (필요 컬럼만 선택)
    @Query("""
            SELECT
              mr.id AS requestId,
              mr.guestId AS guestId,
              mr.guideId AS guideId,
              mr.guideScheduleId AS guideScheduleId,
              mr.destination AS destination,
              mr.concept AS concept,
              mr.conceptSummary AS conceptSummary,
              mr.desiredDate AS desiredDate,
              mr.desiredBudget AS desiredBudget,
              mr.budgetMinWon AS budgetMinWon,
              mr.budgetMaxWon AS budgetMaxWon,
              mr.proposedSchedule AS proposedSchedule,
              mr.proposeMessage AS proposeMessage,
              mr.status AS status,
              mr.createdAt AS createdAt
            FROM MatchRequest mr
            WHERE mr.guestId = :guestId
            ORDER BY mr.createdAt DESC
            """)
    Slice<MatchRequestListProjection> findGuestListProjectionSliceByGuestId(@Param("guestId") Long guestId, Pageable pageable);

    // 가이드 ID로 전체 매칭 요청 조회
    List<MatchRequest> findByGuideId(Long guideId);

    // 게스트 ID + 상태로 조회 (진행중인 투어 확인용)
    List<MatchRequest> findByGuestIdAndStatus(Long guestId, MatchRequestStatus status);

    // 가이드 ID + 상태로 조회
    List<MatchRequest> findByGuideIdAndStatus(Long guideId, MatchRequestStatus status);

    // 당일 진행 대상 매칭 조회 (연장 알림/선택 오픈용)
    List<MatchRequest> findByDesiredDateAndStatusIn(LocalDate desiredDate, List<MatchRequestStatus> statuses);

    /**
     * AI 추천 보정용 집계.
     * 가이드별로 실제 매칭 요청이 몇 번 생성됐는지 계산해 "추천 이후 실제 선택된 정도"를 반영한다.
     */
    @Query("""
            SELECT mr.guideId, COUNT(mr)
            FROM MatchRequest mr
            WHERE mr.guideId IN :guideIds
            GROUP BY mr.guideId
            """)
    List<Object[]> countAllGroupedByGuideId(@Param("guideIds") List<Long> guideIds);

    /**
     * AI 추천 보정용 집계.
     * 요청 생성에서 끝나지 않고 ACCEPTED~COMPLETED 단계까지 이어진 횟수를 세서
     * "실제 진행으로 연결된 가이드"에 소폭 가산점을 줄 때 사용한다.
     */
    @Query("""
            SELECT mr.guideId, COUNT(mr)
            FROM MatchRequest mr
            WHERE mr.guideId IN :guideIds
              AND mr.status IN :statuses
            GROUP BY mr.guideId
            """)
    List<Object[]> countByGuideIdAndStatusInGrouped(
            @Param("guideIds") List<Long> guideIds,
            @Param("statuses") List<MatchRequestStatus> statuses
    );
}
