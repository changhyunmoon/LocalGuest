package com.team6.domain.guestMyPage.dto.response;

import com.team6.domain.guestMyPage.entity.Scrapbook;
import com.team6.domain.guestMyPage.service.ScarapbookService;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ScrapbookSummaryResponse {
    private Long scrapbookId;
    private Long matchRequestId;
    private String title;
    private String mainImageUrl;
    private String tags;
    private LocalDateTime createdAt;

    public static ScrapbookSummaryResponse from(Scrapbook scrapbook) {
        return ScrapbookSummaryResponse.builder()
                .scrapbookId(scrapbook.getId())
                .matchRequestId(scrapbook.getMatchRequestId())
                .title(scrapbook.getTitle())
                .mainImageUrl(scrapbook.getMainImageUrl())
                .tags(scrapbook.getTags())
                .createdAt(scrapbook.getCreatedAt())
                .build();
    }
}
