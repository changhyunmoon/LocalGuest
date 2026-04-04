package com.team6.domain.matching.repository;

import com.team6.domain.matching.entity.TourExtension;
import com.team6.domain.matching.entity.enums.TourExtensionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TourExtensionRepository extends JpaRepository<TourExtension, Long> {

    // 매칭 요청 ID로 연장 신청 조회
    Optional<TourExtension> findByMatchRequest_RequestId(Long requestId);

    // 게스트 ID로 연장 신청 목록 조회
    List<TourExtension> findByGuestId(Long guestId);

    // 자동 취소 스케줄러용 - 마감 지났는데 아직 REQUESTED 상태인 것들
    List<TourExtension> findByStatusAndDeadlineAtBefore(
            TourExtensionStatus status,
            LocalDateTime now
    );
}