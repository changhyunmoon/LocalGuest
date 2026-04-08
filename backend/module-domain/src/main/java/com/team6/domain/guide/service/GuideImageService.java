package com.team6.domain.guide.service;

import com.team6.domain.guide.dto.request.CreateGuideImageRequest;
import com.team6.domain.guide.dto.response.GuideImageResponse;
import com.team6.domain.guide.entity.GuideImage;
import com.team6.domain.guide.entity.GuideProfile;
import com.team6.domain.guide.exception.GuideErrorCode;
import com.team6.domain.guide.exception.GuideException;
import com.team6.domain.guide.repository.GuideImageRepository;
import com.team6.domain.guide.repository.GuideProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 가이드 이미지(피드) 서비스 (F06-03)
 */
@Service
@RequiredArgsConstructor
public class GuideImageService {

    private final GuideImageRepository guideImageRepository;
    private final GuideProfileRepository guideProfileRepository;

    // 이미지 등록 (F06-03)
    @Transactional
    public GuideImageResponse addImage(Long guideId, CreateGuideImageRequest request, Long userId) {
        // 가이드 프로필 조회 및 본인 확인
        GuideProfile profile = getVerifiedProfile(guideId, userId);

        // 이미지 엔티티 생성 및 저장
        GuideImage image = GuideImage.builder()
                .guideProfile(profile)
                .imageUrl(request.getImageUrl())
                .sortOrder(request.getSortOrder())
                .build();

        return GuideImageResponse.from(guideImageRepository.save(image));
    }

    // 이미지 삭제 (F06-03)
    @Transactional
    public void deleteImage(Long imageId, Long guideId, Long userId) {
        // 가이드 프로필 조회 및 본인 확인
        getVerifiedProfile(guideId, userId);

        // 이미지 조회
        GuideImage image = guideImageRepository.findById(imageId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.IMAGE_NOT_FOUND));

        // 해당 가이드의 이미지인지 확인
        if (!image.getGuideProfile().getId().equals(guideId)) {
            throw new GuideException(GuideErrorCode.GUIDE_UNAUTHORIZED);
        }

        guideImageRepository.delete(image);
    }

    // 이미지 목록 조회 — 정렬 순서대로 (F06-03)
    @Transactional(readOnly = true)
    public List<GuideImageResponse> getImages(Long guideId) {
        return guideImageRepository.findByGuideProfile_IdOrderBySortOrderAsc(guideId).stream()
                .map(GuideImageResponse::from)
                .toList();
    }

    // 가이드 프로필 조회 + 본인 여부 확인 공통 메서드
    private GuideProfile getVerifiedProfile(Long guideId, Long userId) {
        GuideProfile profile = guideProfileRepository.findById(guideId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.GUIDE_NOT_FOUND));

        if (!profile.getMemberId().equals(userId)) {
            throw new GuideException(GuideErrorCode.GUIDE_UNAUTHORIZED);
        }

        return profile;
    }
}
