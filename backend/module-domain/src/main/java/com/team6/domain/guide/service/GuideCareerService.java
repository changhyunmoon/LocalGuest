package com.team6.domain.guide.service;

import com.team6.domain.guide.dto.request.CreateGuideCareerRequest;
import com.team6.domain.guide.dto.request.UpdateGuideCareerRequest;
import com.team6.domain.guide.dto.response.GuideCareerResponse;
import com.team6.domain.guide.entity.GuideCareer;
import com.team6.domain.guide.entity.GuideProfile;
import com.team6.domain.guide.exception.GuideErrorCode;
import com.team6.domain.guide.exception.GuideException;
import com.team6.domain.guide.repository.GuideCareerRepository;
import com.team6.domain.guide.repository.GuideProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 가이드 경력 서비스 (F06-01)
 */
@Service
@RequiredArgsConstructor
public class GuideCareerService {

    private final GuideCareerRepository guideCareerRepository;
    private final GuideProfileRepository guideProfileRepository;

    // 경력 등록
    @Transactional
    public GuideCareerResponse addCareer(Long guideId, CreateGuideCareerRequest request, Long userId) {
        // 가이드 프로필 조회 및 본인 확인
        GuideProfile profile = getVerifiedProfile(guideId, userId);

        // 경력 엔티티 생성 및 저장
        GuideCareer career = GuideCareer.builder()
                .guideProfile(profile)
                .title(request.getTitle())
                .description(request.getDescription())
                .acquiredAt(request.getAcquiredAt())
                .build();

        return GuideCareerResponse.from(guideCareerRepository.save(career));
    }

    // 경력 수정
    @Transactional
    public GuideCareerResponse updateCareer(Long careerId, Long guideId, UpdateGuideCareerRequest request, Long userId) {
        // 가이드 프로필 조회 및 본인 확인
        getVerifiedProfile(guideId, userId);

        // 본인 경력인지 확인
        GuideCareer career = guideCareerRepository.findByIdAndGuideProfile_Id(careerId, guideId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.CAREER_NOT_FOUND));

        // 경력 수정
        career.update(request.getTitle(), request.getDescription(), request.getAcquiredAt());

        return GuideCareerResponse.from(career);
    }

    // 경력 삭제
    @Transactional
    public void deleteCareer(Long careerId, Long guideId, Long userId) {
        // 가이드 프로필 조회 및 본인 확인
        getVerifiedProfile(guideId, userId);

        // 본인 경력인지 확인
        GuideCareer career = guideCareerRepository.findByIdAndGuideProfile_Id(careerId, guideId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.CAREER_NOT_FOUND));

        guideCareerRepository.delete(career);
    }

    // 경력 목록 조회
    @Transactional(readOnly = true)
    public List<GuideCareerResponse> getCareers(Long guideId) {
        return guideCareerRepository.findByGuideProfile_Id(guideId).stream()
                .map(GuideCareerResponse::from)
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
