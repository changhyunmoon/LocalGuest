package com.team6.module.openai.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.spi.LlmPromptExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * OpenAI(ChatModel)로 프롬프트를 구조화한 뒤 {@link GuideRecommendRequest}로 만든다.
 * <p>후보 목록·{@code topN}은 호출 인자를 그대로 쓰며, LLM 출력의 해당 필드는 무시한다.
 */
@Slf4j
public final class OpenAiLlmPromptExtractor implements LlmPromptExtractor {

    private static final String SYSTEM_INSTRUCTION = """
            당신은 여행 매칭용 정보 추출기다. 사용자 한국어 입력만 보고 아래 키를 가진 JSON 객체 하나만 출력한다. 설명·마크다운·코드펜스 금지.
            키: region, travelStyle, budgetLevel, budgetMinWon, budgetMaxWon, budgetScope, strictBudget, companionType,
            activityTags, requiredActivityTags, niceToHaveActivityTags, preferredLanguages, requiredLanguages, niceToHaveLanguages,
            allowAdjacentRegion, headcount, durationDays, excludedActivityTags, excludedRegions, excludedTravelStyles, excludedLanguages, softPenaltyActivityTags.
            모르면 null. 배열은 JSON 배열(빈 배열 가능). region은 한국어 지역명이면 그대로(예: 제주, 서울, 부산). 숫자는 정수.
            """;

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public OpenAiLlmPromptExtractor(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
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
            OpenAiChatOptions opts = buildCallOptions();
            Prompt p = new Prompt(
                    List.of(new SystemMessage(SYSTEM_INSTRUCTION), new UserMessage(prompt.trim())),
                    opts
            );
            ChatResponse response = chatModel.call(p);
            String raw = response.getResult().getOutput().getText();
            String json = stripJsonFence(raw);
            if (!StringUtils.hasText(json)) {
                log.warn("[OPEN_AI_PROMPT] 빈 응답, 룰 파서 폴백");
                return Optional.empty();
            }
            LlmGuideRecommendJson parsed = objectMapper.readValue(json, LlmGuideRecommendJson.class);
            GuideRecommendRequest built = toGuideRequest(parsed, topN, guideCandidates);
            if (!StringUtils.hasText(built.getRegion())) {
                log.warn("[OPEN_AI_PROMPT] LLM 결과에 region 없음, 룰 파서 폴백");
                return Optional.empty();
            }
            return Optional.of(built);
        } catch (Exception e) {
            log.warn("[OPEN_AI_PROMPT] LLM 추출 실패, 룰 파서 폴백: {}", e.toString());
            return Optional.empty();
        }
    }

    private OpenAiChatOptions buildCallOptions() {
        OpenAiChatOptions base = chatModel.getDefaultOptions() instanceof OpenAiChatOptions o ? o : null;
        OpenAiChatOptions opts = base != null ? OpenAiChatOptions.fromOptions(base) : new OpenAiChatOptions();
        opts.setTemperature(0.2);
        opts.setResponseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build());
        return opts;
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

    private static GuideRecommendRequest toGuideRequest(
            LlmGuideRecommendJson j,
            int topN,
            List<GuideRecommendRequest.GuideCandidateDto> guideCandidates
    ) {
        return GuideRecommendRequest.builder()
                .region(trimToNull(j.getRegion()))
                .travelStyle(trimToNull(j.getTravelStyle()))
                .budgetLevel(trimToNull(j.getBudgetLevel()))
                .budgetMinWon(j.getBudgetMinWon())
                .budgetMaxWon(j.getBudgetMaxWon())
                .budgetScope(trimToNull(j.getBudgetScope()))
                .strictBudget(j.getStrictBudget())
                .companionType(trimToNull(j.getCompanionType()))
                .activityTags(copyList(j.getActivityTags()))
                .requiredActivityTags(copyList(j.getRequiredActivityTags()))
                .niceToHaveActivityTags(copyList(j.getNiceToHaveActivityTags()))
                .preferredLanguages(copyList(j.getPreferredLanguages()))
                .requiredLanguages(copyList(j.getRequiredLanguages()))
                .niceToHaveLanguages(copyList(j.getNiceToHaveLanguages()))
                .allowAdjacentRegion(j.getAllowAdjacentRegion())
                .headcount(j.getHeadcount())
                .durationDays(j.getDurationDays())
                .excludedActivityTags(copyList(j.getExcludedActivityTags()))
                .excludedRegions(copyList(j.getExcludedRegions()))
                .excludedTravelStyles(copyList(j.getExcludedTravelStyles()))
                .excludedLanguages(copyList(j.getExcludedLanguages()))
                .softPenaltyActivityTags(copyList(j.getSoftPenaltyActivityTags()))
                .topN(topN)
                .guideCandidates(guideCandidates)
                .build();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static List<String> copyList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        List<String> cleaned = raw.stream()
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isEmpty())
                .toList();
        return cleaned.isEmpty() ? null : List.copyOf(cleaned);
    }
}
