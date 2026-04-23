package com.team6.module.gemini.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Generative Language API(Gemini, AI Studio API 키) 설정({@code localguest.gemini.*}).
 * <p>키는 {@code localguest.gemini.api-key}, 환경 변수 {@code GEMINI_API_KEY} 또는 {@code GOOGLE_API_KEY} 순으로 해석한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "localguest.gemini")
public class LocalGuestGeminiProperties {

    /**
     * {@code true}일 때만 Gemini {@link org.springframework.web.client.RestClient}·추출기 빈을 등록한다.
     */
    private boolean enabled = false;

    /** 비어 있지 않으면 최우선으로 사용하는 API 키(로컬 전용). */
    private String apiKey = "";

    /**
     * {@code v1beta/models/{model}:generateContent} 에 쓰는 모델 id(예: gemini-2.0-flash, gemini-1.5-flash).
     */
    private String model = "gemini-2.0-flash";
}
