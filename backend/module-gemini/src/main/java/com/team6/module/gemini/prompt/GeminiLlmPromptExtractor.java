package com.team6.module.gemini.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.llm.LlmGuideRecommendJson;
import com.team6.module.ai.llm.LlmGuideRecommendMapper;
import com.team6.module.ai.llm.LlmPromptExtractionSystemText;
import com.team6.module.ai.spi.LlmPromptExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Optional;

/**
 * Google AI Studio API 키로 {@code generateContent}를 호출해 프롬프트를 구조화한다.
 * <p>후보 목록·{@code topN}은 호출 인자를 그대로 쓴다.
 */
public final class GeminiLlmPromptExtractor implements LlmPromptExtractor {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmPromptExtractor.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String apiKey;

    public GeminiLlmPromptExtractor(
            RestClient restClient,
            ObjectMapper objectMapper,
            String model,
            String apiKey
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.model = model;
        this.apiKey = apiKey;
    }

    @Override
    public Optional<GuideRecommendRequest> tryExtract(
            String prompt,
            int topN,
            List<GuideRecommendRequest.GuideCandidateDto> guideCandidates
    ) {
        if (!StringUtils.hasText(prompt)) {
            return Optional.empty();
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ArrayNode contents = body.putArray("contents");
            ObjectNode userTurn = contents.addObject();
            ArrayNode userParts = userTurn.putArray("parts");
            userParts.addObject().put("text", prompt.trim());

            ObjectNode systemInstruction = body.putObject("systemInstruction");
            ArrayNode sysParts = systemInstruction.putArray("parts");
            sysParts.addObject().put("text", LlmPromptExtractionSystemText.KOREAN_JSON_EXTRACTOR.trim());

            ObjectNode generationConfig = body.putObject("generationConfig");
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.put("temperature", 0.2);

            String payload = objectMapper.writeValueAsString(body);
            String rawJson = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", apiKey)
                            .build(model))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(rawJson)) {
                log.warn("[GEMINI_PROMPT] 빈 응답, 룰 파서 폴백");
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(rawJson);
            if (root.hasNonNull("error")) {
                log.warn("[GEMINI_PROMPT] API 오류, 룰 파서 폴백: {}", root.get("error"));
                return Optional.empty();
            }
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                log.warn("[GEMINI_PROMPT] candidates 없음, 룰 파서 폴백");
                return Optional.empty();
            }
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                log.warn("[GEMINI_PROMPT] parts 없음, 룰 파서 폴백");
                return Optional.empty();
            }
            String text = parts.get(0).path("text").asText("");
            String json = stripJsonFence(text);
            if (!StringUtils.hasText(json)) {
                log.warn("[GEMINI_PROMPT] 본문 JSON 비어 있음, 룰 파서 폴백");
                return Optional.empty();
            }
            LlmGuideRecommendJson parsed = objectMapper.readValue(json, LlmGuideRecommendJson.class);
            GuideRecommendRequest built = LlmGuideRecommendMapper.toRequest(parsed, topN, guideCandidates);
            if (!StringUtils.hasText(built.getRegion())) {
                log.warn("[GEMINI_PROMPT] LLM 결과에 region 없음, 룰 파서 폴백");
                return Optional.empty();
            }
            return Optional.of(built);
        } catch (RestClientResponseException e) {
            log.warn("[GEMINI_PROMPT] HTTP 오류, 룰 파서 폴백: {} {}", e.getStatusCode().value(), e.getStatusText());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[GEMINI_PROMPT] LLM 추출 실패, 룰 파서 폴백: {}", e.toString());
            return Optional.empty();
        }
    }

    private static String stripJsonFence(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) {
                t = t.substring(firstNl + 1);
            }
            int fence = t.lastIndexOf("```");
            if (fence >= 0) {
                t = t.substring(0, fence).trim();
            }
        }
        return t.trim();
    }
}
