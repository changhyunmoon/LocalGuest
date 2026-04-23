package com.team6.domain.guide.service;

import com.team6.domain.guide.dto.request.CreateGuideProfileRequest;
import com.team6.domain.guide.dto.request.UpdateGuideProfileRequest;
import com.team6.domain.guide.dto.request.UpdateGuideRatingRequest;
import com.team6.domain.guide.dto.response.GuideCareerResponse;
import com.team6.domain.guide.dto.response.GuideDetailResponse;
import com.team6.domain.guide.dto.response.GuideFeedResponse;
import com.team6.domain.guide.dto.response.GuideImageResponse;
import com.team6.domain.guide.dto.response.GuideProfileResponse;
import com.team6.domain.guide.dto.response.GuideReviewSummaryResponse;
import com.team6.domain.guide.dto.response.GuideSettlementResponse;
import com.team6.domain.guide.entity.GuideProfile;
import com.team6.domain.guide.exception.GuideErrorCode;
import com.team6.domain.guide.exception.GuideException;
import com.team6.domain.guide.repository.GuideCareerRepository;
import com.team6.domain.guide.repository.GuideFeedRepository;
import com.team6.domain.guide.repository.GuideImageRepository;
import com.team6.domain.guide.repository.GuideProfileRepository;
import com.team6.domain.matching.entity.enums.PaymentStatus;
import com.team6.domain.matching.repository.PaymentRepository;
import com.team6.domain.member.entity.Member;
import com.team6.domain.member.entity.Role;
import com.team6.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 가이드 프로필 서비스 (F06-01, F06-02, F06-06)
 */
@Service
@RequiredArgsConstructor
public class GuideProfileService {

    private final GuideProfileRepository guideProfileRepository;
    private final MemberRepository memberRepository;
    private final GuideFeedRepository guideFeedRepository;
    private final GuideCareerRepository guideCareerRepository;
    private final GuideImageRepository guideImageRepository;
    private final PaymentRepository paymentRepository;

    // 가이드 프로필 등록 (F06-01)
    @Transactional
    public GuideProfileResponse createProfile(CreateGuideProfileRequest request, Long userId) {
        Member requester = memberRepository.findById(userId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.MEMBER_NOT_FOUND));
        Member guideMember = resolveOrCreateGuideMember(requester);

        // GUIDE 멤버 기준으로 이미 프로필이 있으면 중복 등록 차단
        if (guideProfileRepository.existsByMemberId(guideMember.getId())) {
            throw new GuideException(GuideErrorCode.GUIDE_ALREADY_EXISTS);
        }

        // 엔티티 생성 및 저장
        GuideProfile profile = GuideProfile.builder()
                .memberId(guideMember.getId())
                .nickname(request.getNickname())
                .bio(request.getBio())
                .region(request.getRegion())
                .language(request.getLanguage())
                .pricePerHour(request.getPricePerHour())
                // 관리자 승인 단계는 현재 Stub 처리: 신청 즉시 승인 상태로 저장
                .isApproved(true)
                .residenceYears(request.getResidenceYears())
                .localStory(request.getLocalStory())
                .keywords(request.getKeywords())
                .defaultCourse(request.getDefaultCourse())
                .guideStyle(request.getGuideStyle())
                .build();

        GuideProfile saved = guideProfileRepository.save(profile);
        return GuideProfileResponse.from(saved);
    }

    private Member resolveOrCreateGuideMember(Member requester) {
        if (requester.getRole() == Role.GUIDE) {
            return requester;
        }
        return memberRepository.findByEmailAndRole(requester.getEmail(), Role.GUIDE)
                .orElseGet(() -> {
                    String guideNickname = resolveGuideNickname(requester.getNickname(), requester.getEmail());
                    Member guideMember = Member.builder()
                            .email(requester.getEmail())
                            .password(requester.getPassword())
                            .name(requester.getName())
                            .nickname(guideNickname)
                            .role(Role.GUIDE)
                            .status(requester.getStatus())
                            .socialType(requester.getSocialType())
                            .build();
                    return memberRepository.save(guideMember);
                });
    }

    private String resolveGuideNickname(String preferredNickname, String email) {
        if (preferredNickname != null && !preferredNickname.isBlank() && !memberRepository.existsByNickname(preferredNickname)) {
            return preferredNickname;
        }

        String base = (preferredNickname != null && !preferredNickname.isBlank())
                ? preferredNickname + "_guide"
                : email.split("@")[0] + "_guide";

        String candidate = base;
        while (memberRepository.existsByNickname(candidate)) {
            int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
            candidate = base + "_" + suffix;
        }
        return candidate;
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
                request.getLocalStory(),
                request.getKeywords(),
                request.getDefaultCourse(),
                request.getGuideStyle()
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

    // 로그인 사용자의 가이드 프로필 조회 (guideId 추론 안정화용)
    @Transactional(readOnly = true)
    public GuideProfileResponse getMyProfile(Long memberId) {
        GuideProfile profile = guideProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.GUIDE_NOT_FOUND));
        return GuideProfileResponse.from(profile);
    }

    // 가이드 프로필 목록 조회 // 수정 후 — 승인된 + 활성화된 가이드만 반환
    @Transactional(readOnly = true)
    public List<GuideProfileResponse> getProfileList() {
        return guideProfileRepository.findByIsApprovedTrueAndIsActiveTrue().stream()
                .map(GuideProfileResponse::from)
                .toList();
    }

    // 정산 예정 금액 조회 — 본인만 조회 가능 (F06-05)
    @Transactional(readOnly = true)
    public GuideSettlementResponse getExpectedSettlement(Long guideId, Long userId) {
        // 가이드 프로필 조회
        GuideProfile profile = guideProfileRepository.findById(guideId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.GUIDE_NOT_FOUND));

        // 본인 프로필인지 확인
        if (!profile.getMemberId().equals(userId)) {
            throw new GuideException(GuideErrorCode.GUIDE_UNAUTHORIZED);
        }

        BigDecimal expected = paymentRepository
                .findByMatchRequest_GuideIdAndStatus(guideId, PaymentStatus.COMPLETED)
                .stream()
                .map(p -> BigDecimal.valueOf(p.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return GuideSettlementResponse.builder()
                .guideId(guideId)
                .expectedAmount(expected)
                .description("매칭 결제 완료(COMPLETED) 금액 합계(플랫폼 수수료·정산 규칙 적용 전)")
                .build();
    }

    // 가이드 리뷰 요약 조회 — averageRating, reviewCount 반환 (F06-07)
    @Transactional(readOnly = true)
    public GuideReviewSummaryResponse getReviewSummary(Long guideId) {
        // 가이드 프로필 조회
        GuideProfile profile = guideProfileRepository.findById(guideId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.GUIDE_NOT_FOUND));

        return GuideReviewSummaryResponse.from(profile);
    }

    // 평균 평점 및 리뷰 수 갱신 — review 도메인이 리뷰 작성/삭제 후 호출 (F06-07)
    @Transactional
    public GuideProfileResponse updateRating(Long guideId, UpdateGuideRatingRequest request) {
        // 가이드 프로필 조회
        GuideProfile profile = guideProfileRepository.findById(guideId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.GUIDE_NOT_FOUND));

        // 평점 및 리뷰 수 갱신
        profile.updateRating(request.getAverageRating(), request.getReviewCount());

        return GuideProfileResponse.from(profile);
    }

    // 관리자 승인 (F06-01) — isApproved false → true
    @Transactional
    public GuideProfileResponse approveProfile(Long guideId) {
        GuideProfile profile = guideProfileRepository.findById(guideId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.GUIDE_NOT_FOUND));

        if (profile.getIsApproved()) {
            throw new GuideException(GuideErrorCode.GUIDE_ALREADY_APPROVED);
        }

        profile.approve();
        return GuideProfileResponse.from(profile);
    }

    // 가이드 상세 통합 조회 — 프로필 + 피드 + 경력 + 이미지 단건 반환
    @Transactional(readOnly = true)
    public GuideDetailResponse getDetail(Long guideId) {
        GuideProfile profile = guideProfileRepository.findById(guideId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.GUIDE_NOT_FOUND));

        return GuideDetailResponse.builder()
                .profile(GuideProfileResponse.from(profile))
                .feeds(guideFeedRepository
                        .findByGuideProfile_IdAndIsDeletedFalseOrderByCreatedAtDesc(guideId)
                        .stream().map(GuideFeedResponse::fullFrom).toList())
                .careers(guideCareerRepository
                        .findByGuideProfile_Id(guideId)
                        .stream().map(GuideCareerResponse::from).toList())
                .images(guideImageRepository
                        .findByGuideProfile_IdOrderBySortOrderAsc(guideId)
                        .stream().map(GuideImageResponse::from).toList())
                .build();
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
