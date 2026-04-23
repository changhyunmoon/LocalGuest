package com.team6.module.openai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI 호출 스위치 및 기본 모델({@code localguest.openai.*}).
 * <p>API 키는 {@code spring.ai.openai.api-key} 또는 환경 변수 {@code SPRING_AI_OPENAI_API_KEY}를
 * 우선 사용하고, 필요 시 {@code localguest.openai.api-key}로 덮어쓸 수 있다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "localguest.openai")
public class LocalGuestOpenAiProperties {

    /**
     * {@code true}일 때만 {@link org.springframework.ai.openai.OpenAiChatModel} 빈을 등록한다.
     */
    private boolean enabled = false;

    /**
     * 비어 있지 않으면 최우선으로 사용하는 API 키(로컬 전용). 비밀 저장소에만 둔다.
     */
    private String apiKey = "";

    /**
     * 기본 채팅 모델명(OpenAI API 기준).
     */
    private String model = "gpt-4o-mini";
}
