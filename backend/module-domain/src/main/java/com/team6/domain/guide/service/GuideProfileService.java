package com.team6.domain.guide.service;

import com.team6.domain.guide.dto.request.CreateGuideProfileRequest;
import com.team6.domain.guide.dto.request.UpdateGuideProfileRequest;
import com.team6.domain.guide.dto.response.GuideProfileResponse;
import com.team6.domain.guide.entity.GuideProfile;
import com.team6.domain.guide.exception.GuideErrorCode;
import com.team6.domain.guide.exception.GuideException;
import com.team6.domain.guide.repository.GuideProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 가이드 프로필 서비스 (F06-01, F06-02, F06-06)
 */
@Service
@RequiredArgsConstructor
public class GuideProfileService {

    private final GuideProfileRepository guideProfileRepository;

    // 가이드 프로필 등록 (F06-01)
    @Transactional
    public GuideProfileResponse createProfile(CreateGuideProfileRequest request, Long userId) {
        // 이미 등록된 가이드인지 확인
        if (guideProfileRepository.existsByMemberId(userId)) {
            throw new GuideException(GuideErrorCode.GUIDE_ALREADY_EXISTS);
        }

        // 엔티티 생성 및 저장
        GuideProfile profile = GuideProfile.builder()
                .memberId(userId)
                .nickname(request.getNickname())
                .profileImage(request.getProfileImage())
                .bio(request.getBio())
                .region(request.getRegion())
                .language(request.getLanguage())
                .pricePerHour(request.getPricePerHour())
                .residenceYears(request.getResidenceYears())
                .localStory(request.getLocalStory())
                .build();

        return GuideProfileResponse.from(guideProfileRepository.save(profile));
    }

    // 가이드 프로필 수정 (F06-01)
    @Transactional
    public GuideProfileResponse updateProfile(Long guideId, UpdateGuideProfileRequest request, Long userId) {
        // 가이드 프로필 조회
        GuideProfile profile = guideProfileRepository.findById(guideId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.GUIDE_NOT_FOUND));

        // 본인 프로필인지 확인
        if (!profile.getMemberId().equals(userId)) {
            throw new GuideException(GuideErrorCode.GUIDE_UNAUTHORIZED);
        }

        // 프로필 수정
        profile.update(
                request.getNickname(),
                request.getProfileImage(),
                request.getBio(),
                request.getRegion(),
                request.getLanguage(),
                request.getPricePerHour(),
                request.getIsActive(),
                request.getResidenceYears(),
                request.getLocalStory()
        );

        return GuideProfileResponse.from(profile);
    }

    // 가이드 프로필 단건 조회
    @Transactional(readOnly = true)
    public GuideProfileResponse getProfile(Long guideId) {
        GuideProfile profile = guideProfileRepository.findById(guideId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.GUIDE_NOT_FOUND));

        return GuideProfileResponse.from(profile);
    }

    // 가이드 프로필 목록 조회
    @Transactional(readOnly = true)
    public List<GuideProfileResponse> getProfileList() {
        return guideProfileRepository.findAll().stream()
                .map(GuideProfileResponse::from)
                .toList();
    }

    // 가이드 활성화/비활성화 토글 (F06-06)
    @Transactional
    public GuideProfileResponse toggleActive(Long guideId, Long userId) {
        // 가이드 프로필 조회
        GuideProfile profile = guideProfileRepository.findById(guideId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.GUIDE_NOT_FOUND));

        // 본인 프로필인지 확인
        if (!profile.getMemberId().equals(userId)) {
            throw new GuideException(GuideErrorCode.GUIDE_UNAUTHORIZED);
        }

        // 활성화 상태 반전
        profile.toggleActive();

        return GuideProfileResponse.from(profile);
    }
}
