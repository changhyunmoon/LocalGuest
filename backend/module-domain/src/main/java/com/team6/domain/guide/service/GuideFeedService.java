package com.team6.domain.guide.service;

import com.team6.domain.guide.dto.request.CreateGuideFeedRequest;
import com.team6.domain.guide.dto.request.UpdateGuideFeedRequest;
import com.team6.domain.guide.dto.response.GuideFeedResponse;
import com.team6.domain.guide.entity.GuideFeed;
import com.team6.domain.guide.entity.GuideProfile;
import com.team6.domain.guide.exception.GuideErrorCode;
import com.team6.domain.guide.exception.GuideException;
import com.team6.domain.guide.repository.GuideFeedRepository;
import com.team6.domain.guide.repository.GuideProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.IntStream;

import java.util.List;

/**
 * 가이드 피드 서비스 (F06-03)
 */
@Service
@RequiredArgsConstructor
public class GuideFeedService {

    private final GuideFeedRepository guideFeedRepository;
    private final GuideProfileRepository guideProfileRepository;

    // 피드 등록
    @Transactional
    public GuideFeedResponse createFeed(Long guideId, CreateGuideFeedRequest request, Long userId) {
        // 가이드 프로필 조회 및 본인 확인
        GuideProfile profile = getVerifiedProfile(guideId, userId);

        // 피드 엔티티 생성 및 저장
        GuideFeed feed = GuideFeed.builder()
                .guideProfile(profile)
                .content(request.getContent())
                .imageUrl(request.getImageUrl())
                .build();

        return GuideFeedResponse.fullFrom(guideFeedRepository.save(feed));
    }

    // 피드 수정
    @Transactional
    public GuideFeedResponse updateFeed(Long guideId, Long feedId, UpdateGuideFeedRequest request, Long userId) {
        // 가이드 프로필 조회 및 본인 확인
        getVerifiedProfile(guideId, userId);

        // 피드 조회 및 본인 소유 확인
        GuideFeed feed = getVerifiedFeed(feedId, guideId);

        // 피드 내용 수정
        feed.update(request.getContent(), request.getImageUrl());

        return GuideFeedResponse.fullFrom(feed);
    }

    // 피드 소프트 삭제
    @Transactional
    public void deleteFeed(Long guideId, Long feedId, Long userId) {
        // 가이드 프로필 조회 및 본인 확인
        getVerifiedProfile(guideId, userId);

        // 피드 조회 및 본인 소유 확인
        GuideFeed feed = getVerifiedFeed(feedId, guideId);

        // 삭제 플래그 설정 (실제 DB 삭제 없이 isDeleted = true)
        feed.delete();
    }

    // 피드 목록 조회 — 매칭 여부에 따라 공개 범위 결정 (F06-03)
    @Transactional(readOnly = true)
    public List<GuideFeedResponse> getFeeds(Long guideId, boolean isMatched) {
        // 삭제되지 않은 피드만 최신순으로 조회
        List<GuideFeed> feeds = guideFeedRepository
                .findByGuideProfile_IdAndIsDeletedFalseOrderByCreatedAtDesc(guideId);

        if (isMatched) {
            // 매칭 완료 시 전체 공개
            return feeds.stream()
                    .map(GuideFeedResponse::fullFrom)
                    .toList();
        }

        // 매칭 전: 앞 절반 공개, 나머지 절반 잠금 처리
        int totalSize = feeds.size();
        int openCount = (totalSize + 1) / 2; // 홀수일 때 앞쪽이 더 많도록 올림

        // 인덱스를 직접 생성해서 순서 보장 (indexOf는 내용 기반 비교라 중복 시 오작동)
        return IntStream.range(0, totalSize)
                .mapToObj(i -> i < openCount
                        ? GuideFeedResponse.fullFrom(feeds.get(i))    // 앞 절반: 공개
                        : GuideFeedResponse.lockedFrom(feeds.get(i))) // 뒤 절반: 잠금 (결제/매칭 후 공개)
                .toList();
    }

    // 가이드 프로필 조회 + 본인 여부 확인 공통 메서드
    private GuideProfile getVerifiedProfile(Long guideId, Long userId) {
        GuideProfile profile = guideProfileRepository.findById(guideId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.GUIDE_NOT_FOUND));

        // 본인 프로필인지 확인
        if (!profile.getMemberId().equals(userId)) {
            throw new GuideException(GuideErrorCode.GUIDE_UNAUTHORIZED);
        }

        return profile;
    }

    // 피드 조회 + 해당 가이드 소유 확인 공통 메서드
    private GuideFeed getVerifiedFeed(Long feedId, Long guideId) {
        GuideFeed feed = guideFeedRepository.findByIdAndIsDeletedFalse(feedId)
                .orElseThrow(() -> new GuideException(GuideErrorCode.FEED_NOT_FOUND));

        // 해당 가이드의 피드인지 확인
        if (!feed.getGuideProfile().getId().equals(guideId)) {
            throw new GuideException(GuideErrorCode.GUIDE_UNAUTHORIZED);
        }

        return feed;
    }
}
