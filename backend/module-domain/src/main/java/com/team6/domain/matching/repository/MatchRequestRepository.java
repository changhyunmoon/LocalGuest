package com.team6.domain.matching.repository;

import com.team6.domain.matching.entity.MatchRequest;
import com.team6.domain.matching.entity.enums.MatchRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
}