package com.team6.domain.guide.dto.response;

import com.team6.domain.guide.entity.GuideFeed;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 가이드 피드 응답 DTO (F06-03)
 */
@Getter
@Builder
public class GuideFeedResponse {

    private Long feedId;             // 피드 ID
    private String content;          // 피드 본문
    private String imageUrl;         // 피드 이미지 URL
    private LocalDateTime createdAt; // 피드 등록일시

    // 피드 응답 변환 (F06-03)
    public static GuideFeedResponse fullFrom(GuideFeed feed) {
        return GuideFeedResponse.builder()
                .feedId(feed.getId())
                .content(feed.getContent())
                .imageUrl(feed.getImageUrl())
                .createdAt(feed.getCreatedAt())
                .build();
    }
}
