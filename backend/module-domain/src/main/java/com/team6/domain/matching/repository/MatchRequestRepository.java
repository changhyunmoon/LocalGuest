package com.team6.domain.matching.repository;

import com.team6.domain.matching.entity.MatchRequest;
import com.team6.domain.matching.entity.enums.MatchRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {

    // 게스트 ID로 전체 매칭 요청 조회
    List<MatchRequest> findByGuestId(Long guestId);

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
