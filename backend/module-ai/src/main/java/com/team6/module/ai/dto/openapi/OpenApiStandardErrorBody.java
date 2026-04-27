package com.team6.module.ai.dto.openapi;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Spring Boot 기본 오류 JSON 형태를 OpenAPI 예시로만 사용한다(실제 필드는 버전·설정에 따라 다를 수 있음).
 */
@Schema(name = "OpenApiStandardErrorBody", description = "잘못된 요청·서버 오류 시 Spring 기본 오류 응답(예시)")
@Getter
@Setter
public class OpenApiStandardErrorBody {
    @Schema(example = "2026-04-09T12:00:00")
    private String timestamp;
    @Schema(example = "400")
    private int status;
    @Schema(example = "Bad Request")
    private String error;
    @Schema(example = "/api/ai/recommend")
    private String path;
    @Schema(example = "JSON parse error")
    private String message;
}
