package com.team6.domain.guide.dto.response;

import com.team6.domain.guide.entity.GuideFeed;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 가이드 피드 응답 DTO (F06-03)
 */
@Getter
@Builder
public class GuideFeedResponse {

    private Long feedId;             // 피드 ID
    private String content;          // 피드 본문
    private String imageUrl;         // 피드 이미지 URL (하위 호환용 — imageUrls[0])
    private List<String> imageUrls;  // 다중 이미지 URL 목록
    private LocalDateTime createdAt; // 피드 등록일시

    // 피드 응답 변환 (F06-03)
    public static GuideFeedResponse fullFrom(GuideFeed feed) {
        List<String> urls = splitImageUrls(feed.getImageUrl());
        return GuideFeedResponse.builder()
                .feedId(feed.getId())
                .content(feed.getContent())
                .imageUrl(feed.getImageUrl())
                .imageUrls(urls)
                .createdAt(feed.getCreatedAt())
                .build();
    }

    // imageUrl 콤마 구분 문자열 → List 변환
    private static List<String> splitImageUrls(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return Collections.emptyList();
        return Arrays.stream(imageUrl.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }
}
