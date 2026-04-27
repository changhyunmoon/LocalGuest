package com.team6.domain.guide.repository;

import com.team6.domain.guide.entity.GuideImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 가이드 이미지 레포지토리 (guide_images 테이블)
 */
public interface GuideImageRepository extends JpaRepository<GuideImage, Long> {

    // 가이드 이미지 정렬 순서대로 조회 (F06-03)
    List<GuideImage> findByGuideProfile_IdOrderBySortOrderAsc(Long guideId);

    // 가이드 이미지 전체 삭제 (재업로드 시) (F06-03)
    void deleteByGuideProfile_Id(Long guideId);
}
