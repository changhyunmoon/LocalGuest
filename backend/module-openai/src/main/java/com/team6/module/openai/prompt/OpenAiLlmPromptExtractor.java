package com.team6.module.openai.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.llm.LlmGuideRecommendJson;
import com.team6.module.ai.llm.LlmGuideRecommendMapper;
import com.team6.module.ai.llm.LlmPromptExtractionSystemText;
import com.team6.module.ai.spi.LlmPromptExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public final class OpenAiLlmPromptExtractor implements LlmPromptExtractor {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmPromptExtractor.class);

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
                    List.of(
                            new SystemMessage(LlmPromptExtractionSystemText.KOREAN_JSON_EXTRACTOR),
                            new UserMessage(prompt.trim())
                    ),
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
            GuideRecommendRequest built = LlmGuideRecommendMapper.toRequest(parsed, topN, guideCandidates);
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
}
