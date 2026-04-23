package com.team6.module.gemini.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team6.module.ai.dto.request.GuideRecommendRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiLlmPromptExtractorTest {

    private MockRestServiceServer server;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        restClient = builder.build();
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    void tryExtract_parsesGeminiEnvelope() {
        server.expect(request -> {
                    String p = request.getURI().getPath();
                    if (!p.contains("generateContent")) {
                        throw new AssertionError("unexpected path: " + p);
                    }
                    String q = request.getURI().getQuery();
                    if (q == null || !q.contains("key=test-key")) {
                        throw new AssertionError("unexpected query: " + q);
                    }
                })
                .andRespond(withSuccess(
                        "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"region\\\":\\\"제주\\\",\\\"activityTags\\\":[\\\"맛집\\\"]}\"}]}}]}",
                        MediaType.APPLICATION_JSON
                ));

        GeminiLlmPromptExtractor ext = new GeminiLlmPromptExtractor(restClient, new ObjectMapper(), "gemini-2.0-flash", "test-key");
        Optional<GuideRecommendRequest> out = ext.tryExtract("제주", 3, List.of());

        assertThat(out).isPresent();
        assertThat(out.get().getRegion()).isEqualTo("제주");
        assertThat(out.get().getActivityTags()).containsExactly("맛집");
        assertThat(out.get().getTopN()).isEqualTo(3);
    }
}
