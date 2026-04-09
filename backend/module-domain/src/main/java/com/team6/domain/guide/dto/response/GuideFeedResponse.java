package com.team6.domain.guide.dto.response;

import com.team6.domain.guide.entity.GuideFeed;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 가이드 피드 응답 DTO (F06-03)
 * locked 필드로 매칭 전/후 공개 범위 구분
 */
@Getter
@Builder
public class GuideFeedResponse {

    private Long feedId;             // 피드 ID
    private String content;          // 피드 본문 (locked 시 null)
    private String imageUrl;         // 피드 이미지 URL (locked 시 null)
    private boolean locked;          // 잠금 여부 (매칭 전 절반 비공개)
    private LocalDateTime createdAt; // 피드 등록일시

    // 전체 공개 응답 — 매칭 완료된 피드 또는 공개 대상 피드 (F06-03)
    public static GuideFeedResponse fullFrom(GuideFeed feed) {
        return GuideFeedResponse.builder()
                .feedId(feed.getId())
                .content(feed.getContent())
                .imageUrl(feed.getImageUrl())
                .locked(false) // 잠금 해제 상태
                .createdAt(feed.getCreatedAt())
                .build();
    }

    // 잠금 응답 — 매칭 전 비공개 대상 피드 (content, imageUrl null 처리) (F06-03)
    public static GuideFeedResponse lockedFrom(GuideFeed feed) {
        return GuideFeedResponse.builder()
                .feedId(feed.getId())
                .content(null)    // 내용 비공개
                .imageUrl(null)   // 이미지 비공개
                .locked(true)     // 잠금 상태
                .createdAt(feed.getCreatedAt())
                .build();
    }
}
