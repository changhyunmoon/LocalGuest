package com.team6.domain.guestMyPage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ScrapbookCreateRequest {
    @NotNull
    private Long matchRequestId;

    @NotBlank
    private String title;

    @NotBlank
    private String content;

    private String mainImgaeUrl;

    private String tags;
}
