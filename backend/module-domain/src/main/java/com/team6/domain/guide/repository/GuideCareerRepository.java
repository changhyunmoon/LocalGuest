package com.team6.domain.guide.repository;

import com.team6.domain.guide.entity.GuideCareer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 가이드 경력 레포지토리 (guide_careers 테이블)
 */
public interface GuideCareerRepository extends JpaRepository<GuideCareer, Long> {

    // 가이드 경력 목록 조회 — 프로필 상세 조회 시 사용 (F06-01)
    List<GuideCareer> findByGuideProfile_Id(Long guideId);

    // 여러 가이드 경력 목록 조회 — AI 후보 집계용
    List<GuideCareer> findByGuideProfile_IdIn(List<Long> guideIds);

    // 경력 단건 조회 — 본인 소유 여부 확인 (F06-01)
    Optional<GuideCareer> findByIdAndGuideProfile_Id(Long id, Long guideId);
}
