package com.team6.module.openai.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.llm.LlmGuideRankJson;
import com.team6.module.ai.llm.LlmGuideRankResult;
import com.team6.module.ai.llm.LlmGuideRankSystemText;
import com.team6.module.ai.llm.LlmRankCardComposer;
import com.team6.module.ai.spi.LlmGuideRanker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * OpenAI(ChatModel)로 후보 가이드 순위를 정한다.
 */
public final class OpenAiLlmGuideRanker implements LlmGuideRanker {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmGuideRanker.class);

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public OpenAiLlmGuideRanker(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<LlmGuideRankResult> tryRank(
            String userPrompt,
            List<GuideRecommendRequest.GuideCandidateDto> candidates,
            int topN,
            long tieBreakSeed
    ) {
        if (!StringUtils.hasText(userPrompt) || candidates == null || candidates.isEmpty() || topN <= 0) {
            return Optional.empty();
        }
        List<GuideRecommendRequest.GuideCandidateDto> sorted = LlmRankCardComposer.sortByQualityForPrompt(candidates);
        String userContent = LlmRankCardComposer.buildRankUserContent(userPrompt, sorted, tieBreakSeed);
        try {
            OpenAiChatOptions opts = buildCallOptions();
            Prompt p = new Prompt(
                    List.of(
                            new SystemMessage(LlmGuideRankSystemText.KOREAN_JSON_RANKER),
                            new UserMessage(userContent)
                    ),
                    opts
            );
            ChatResponse response = chatModel.call(p);
            String raw = response.getResult().getOutput().getText();
            String json = stripJsonFence(raw);
            if (!StringUtils.hasText(json)) {
                log.warn("[OPEN_AI_RANK] 빈 응답");
                return Optional.empty();
            }
            LlmGuideRankJson parsed = objectMapper.readValue(json, LlmGuideRankJson.class);
            List<Long> ids = parsed.getOrderedGuideIds();
            if (ids == null || ids.isEmpty()) {
                return Optional.empty();
            }
            Set<Long> allowed = new HashSet<>();
            for (GuideRecommendRequest.GuideCandidateDto c : candidates) {
                if (c != null && c.getGuideId() != null) {
                    allowed.add(c.getGuideId());
                }
            }
            LinkedHashSet<Long> dedup = new LinkedHashSet<>();
            for (Long id : ids) {
                if (id == null || !allowed.contains(id)) {
                    continue;
                }
                dedup.add(id);
                if (dedup.size() >= topN) {
                    break;
                }
            }
            if (dedup.isEmpty()) {
                log.warn("[OPEN_AI_RANK] 검증 후 순위가 비었음");
                return Optional.empty();
            }
            Map<Long, String> reasons = parsed.reasonsByGuideId();
            return Optional.of(new LlmGuideRankResult(new ArrayList<>(dedup), reasons));
        } catch (Exception e) {
            log.warn("[OPEN_AI_RANK] LLM 순위 실패: {}", e.toString());
            return Optional.empty();
        }
    }

    private OpenAiChatOptions buildCallOptions() {
        OpenAiChatOptions base = chatModel.getDefaultOptions() instanceof OpenAiChatOptions o ? o : null;
        OpenAiChatOptions opts = base != null ? OpenAiChatOptions.fromOptions(base) : new OpenAiChatOptions();
        opts.setTemperature(0.35);
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
