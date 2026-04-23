package com.team6.module.openai.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import com.team6.module.ai.support.ConceptSummaryGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiLlmPromptExtractorTest {

    @Mock
    private ChatModel chatModel;

    @Test
    void tryExtract_mapsJson_and_fixesTopNAndCandidates() {
        OpenAiChatOptions def = new OpenAiChatOptions();
        def.setModel("gpt-4o-mini");
        when(chatModel.getDefaultOptions()).thenReturn(def);
        when(chatModel.call(any(Prompt.class))).thenReturn(ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(
                        "{\"region\":\"제주\",\"activityTags\":[\"맛집\"],\"guideBullets\":[\"맛집 위주로 돌아다니고 싶어\"]}"
                ))))
                .build());

        OpenAiLlmPromptExtractor ext = new OpenAiLlmPromptExtractor(chatModel, new ObjectMapper());
        GuideRecommendRequest.GuideCandidateDto c = GuideRecommendRequest.GuideCandidateDto.builder()
                .guideId(9L)
                .guideName("가이드")
                .region("제주")
                .build();
        Optional<GuideRecommendRequest> out = ext.tryExtract("제주 맛집", 5, List.of(c));

        assertThat(out).isPresent();
        assertThat(out.get().getRegion()).isEqualTo("제주");
        assertThat(out.get().getTopN()).isEqualTo(5);
        assertThat(out.get().getGuideCandidates()).containsExactly(c);
        assertThat(out.get().getActivityTags()).containsExactly("맛집");
        assertThat(ConceptSummaryGenerator.generate(out.get())).contains("• 맛집");
    }

    @Test
    void tryExtract_masksPhoneInSpecialRequests() {
        OpenAiChatOptions def = new OpenAiChatOptions();
        def.setModel("gpt-4o-mini");
        when(chatModel.getDefaultOptions()).thenReturn(def);
        when(chatModel.call(any(Prompt.class))).thenReturn(ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(
                        "{\"region\":\"서울\",\"specialRequests\":\"연락 010-1234-5678\"}"
                ))))
                .build());

        OpenAiLlmPromptExtractor ext = new OpenAiLlmPromptExtractor(chatModel, new ObjectMapper());
        Optional<GuideRecommendRequest> out = ext.tryExtract("서울", 2, List.of());
        assertThat(out).isPresent();
        assertThat(out.get().getLlmSpecialRequests()).contains("[연락처 생략]");
    }

    @Test
    void tryExtract_emptyRegion_returnsEmpty() {
        OpenAiChatOptions def = new OpenAiChatOptions();
        def.setModel("gpt-4o-mini");
        when(chatModel.getDefaultOptions()).thenReturn(def);
        when(chatModel.call(any(Prompt.class))).thenReturn(ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage("{}"))))
                .build());

        OpenAiLlmPromptExtractor ext = new OpenAiLlmPromptExtractor(chatModel, new ObjectMapper());
        assertThat(ext.tryExtract("?", 2, List.of())).isEmpty();
    }
}
