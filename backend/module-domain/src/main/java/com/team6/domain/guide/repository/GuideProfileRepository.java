package com.team6.domain.guide.repository;

import com.team6.domain.guide.entity.GuideProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 가이드 프로필 레포지토리 (guide_profiles 테이블)
 */
public interface GuideProfileRepository extends JpaRepository<GuideProfile, Long> {

    // memberId 기준으로 가이드 프로필 조회 (F06-01, 02, 06)
    Optional<GuideProfile> findByMemberId(Long memberId);

    // 프로필 중복 등록 방지 확인 (F06-01)
    boolean existsByMemberId(Long memberId);
}
